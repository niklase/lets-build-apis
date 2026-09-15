package com.zuunr.dcentb.async.config;

import com.zuunr.dcentb.async.taskprocessing.Subscriber;
import com.zuunr.dcentb.async.taskprocessing.TaskProcessor;
import com.zuunr.dcentb.async.taskprocessing.TaskProcessorResolver;
import com.zuunr.dcentb.async.taskprocessing.Topic;
import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Parses {@code x-dcentb.asyncProcessing} and {@code x-dcentb.topics} out of the one OpenAPI document
 * dcentb loads (docs/async-tasks-processing.md §9.2/§14 — confirmed to live inside that single
 * document, not a separate config file, since a deployment only ever loads one). Absence of
 * {@code x-dcentb.asyncProcessing} means the feature is off for this deployment — {@link #parse}
 * returns {@link Optional#empty()}, and nothing async-related starts. This is deliberate: most
 * existing deployments (demo.openapi.json, person.openapi.secret.json, todo-api.openapi.json) don't
 * declare it, and change streams need a replica set (README.md, "Start MongoDB") that a standalone
 * deployment may not have — opt-in keeps this from breaking anything that hasn't asked for it.
 *
 * <p>Only settings the current implementation actually reads are parsed here. Per-topic/subscriber
 * {@code maxRetries} overrides and the {@code resume} policy knobs (manual resume-timestamp override,
 * per docs/async-tasks-processing.md §8.2) are still intentionally not parsed — neither
 * {@link com.zuunr.dcentb.async.taskprocessing.TopicRouter} nor
 * {@link com.zuunr.dcentb.async.changestream.ChangeStreamListener} act on them yet, and parsing
 * config nothing reads would be misleading. Add parsing for a field exactly when the code that
 * consumes it is built — the {@code deadLetterQueue}/{@code errorQueue} backoff fields below were
 * added in that same step as {@code ErrorQueueProcessor} itself, not before.
 */
public final class AsyncProcessingSettings {

    private final String streamId;
    private final String leaseCollection;
    private final Duration leaseDuration;
    private final Duration heartbeatInterval;
    private final String checkpointCollection;
    private final String errorQueueCollection;
    private final String messageLogCollection;
    private final Duration errorQueuePollInterval;
    private final Duration errorQueueBaseBackoff;
    private final Duration errorQueueMaxBackoff;
    private final String deadLetterCollection;
    private final int maxErrorQueueRequeues;
    private final List<Topic> topics;

    private AsyncProcessingSettings(String streamId, String leaseCollection, Duration leaseDuration, Duration heartbeatInterval,
                                     String checkpointCollection, String errorQueueCollection, String messageLogCollection,
                                     Duration errorQueuePollInterval, Duration errorQueueBaseBackoff, Duration errorQueueMaxBackoff,
                                     String deadLetterCollection, int maxErrorQueueRequeues, List<Topic> topics) {
        this.streamId = streamId;
        this.leaseCollection = leaseCollection;
        this.leaseDuration = leaseDuration;
        this.heartbeatInterval = heartbeatInterval;
        this.checkpointCollection = checkpointCollection;
        this.errorQueueCollection = errorQueueCollection;
        this.messageLogCollection = messageLogCollection;
        this.errorQueuePollInterval = errorQueuePollInterval;
        this.errorQueueBaseBackoff = errorQueueBaseBackoff;
        this.errorQueueMaxBackoff = errorQueueMaxBackoff;
        this.deadLetterCollection = deadLetterCollection;
        this.maxErrorQueueRequeues = maxErrorQueueRequeues;
        this.topics = topics;
    }

    public static Optional<AsyncProcessingSettings> parse(JsonObject openApiDocument) {
        JsonObject xDcentb = openApiDocument.get("x-dcentb", JsonObject.EMPTY).getJsonObject();
        JsonValue asyncProcessingValue = xDcentb.get("asyncProcessing");
        if (asyncProcessingValue == null) {
            return Optional.empty();
        }
        JsonObject asyncProcessing = asyncProcessingValue.getJsonObject();

        String streamId = asyncProcessing.get("streamId", "default").getString();

        JsonObject leaderElection = asyncProcessing.get("leaderElection", JsonObject.EMPTY).getJsonObject();
        String leaseCollection = leaderElection.get("collection", "async-leader-lease").getString();
        int leaseDurationSeconds = leaderElection.get("leaseDurationSeconds", 30).getInteger();
        int heartbeatIntervalSeconds = leaderElection.get("heartbeatIntervalSeconds", 10).getInteger();

        String checkpointCollection = asyncProcessing.get("checkpoint", JsonObject.EMPTY).getJsonObject()
                .get("collection", "async-checkpoints").getString();
        String messageLogCollection = asyncProcessing.get("messageLog", JsonObject.EMPTY).getJsonObject()
                .get("collection", "async-topic-messages").getString();

        JsonObject errorQueue = asyncProcessing.get("errorQueue", JsonObject.EMPTY).getJsonObject();
        String errorQueueCollection = errorQueue.get("collection", "async-processing-errors").getString();
        int pollIntervalSeconds = errorQueue.get("pollIntervalSeconds", 30).getInteger();
        int baseBackoffSeconds = errorQueue.get("baseBackoffSeconds", 30).getInteger();
        int maxBackoffSeconds = errorQueue.get("maxBackoffSeconds", 1800).getInteger();

        JsonObject deadLetterQueue = asyncProcessing.get("deadLetterQueue", JsonObject.EMPTY).getJsonObject();
        String deadLetterCollection = deadLetterQueue.get("collection", "async-processing-dead-letters").getString();
        int maxErrorQueueRequeues = deadLetterQueue.get("maxErrorQueueRequeues", 5).getInteger();

        JsonArray topicsJson = xDcentb.get("topics", JsonArray.EMPTY).getJsonArray();
        List<Topic> topics = new ArrayList<>();
        for (int i = 0; i < topicsJson.size(); i++) {
            topics.add(parseTopic(topicsJson.get(i).getJsonObject()));
        }

        return Optional.of(new AsyncProcessingSettings(streamId, leaseCollection,
                Duration.ofSeconds(leaseDurationSeconds), Duration.ofSeconds(heartbeatIntervalSeconds),
                checkpointCollection, errorQueueCollection, messageLogCollection,
                Duration.ofSeconds(pollIntervalSeconds), Duration.ofSeconds(baseBackoffSeconds), Duration.ofSeconds(maxBackoffSeconds),
                deadLetterCollection, maxErrorQueueRequeues, topics));
    }

    private static Topic parseTopic(JsonObject topicJson) {
        String name = topicJson.get("name").getString();
        String sourceCollection = topicJson.get("sourceCollection").getString();
        JsonArray subscribersJson = topicJson.get("subscribers").getJsonArray();

        List<Subscriber> subscribers = new ArrayList<>();
        for (int i = 0; i < subscribersJson.size(); i++) {
            JsonObject subscriberJson = subscribersJson.get(i).getJsonObject();
            String subscriberName = subscriberJson.get("name").getString();
            String taskProcessorClass = subscriberJson.get("taskProcessorClass").getString();
            TaskProcessor taskProcessor = TaskProcessorResolver.resolve(taskProcessorClass, subscriberJson.jsonValue());
            subscribers.add(new Subscriber(subscriberName, taskProcessor));
        }

        return new Topic(name, sourceCollection, subscribers);
    }

    public String getStreamId() {
        return streamId;
    }

    public String getLeaseCollection() {
        return leaseCollection;
    }

    public Duration getLeaseDuration() {
        return leaseDuration;
    }

    public Duration getHeartbeatInterval() {
        return heartbeatInterval;
    }

    public String getCheckpointCollection() {
        return checkpointCollection;
    }

    public String getErrorQueueCollection() {
        return errorQueueCollection;
    }

    public String getMessageLogCollection() {
        return messageLogCollection;
    }

    public Duration getErrorQueuePollInterval() {
        return errorQueuePollInterval;
    }

    public Duration getErrorQueueBaseBackoff() {
        return errorQueueBaseBackoff;
    }

    public Duration getErrorQueueMaxBackoff() {
        return errorQueueMaxBackoff;
    }

    public String getDeadLetterCollection() {
        return deadLetterCollection;
    }

    public int getMaxErrorQueueRequeues() {
        return maxErrorQueueRequeues;
    }

    public List<Topic> getTopics() {
        return topics;
    }
}
