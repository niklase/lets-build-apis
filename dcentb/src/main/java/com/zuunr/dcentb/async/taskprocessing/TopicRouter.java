package com.zuunr.dcentb.async.taskprocessing;

import com.zuunr.dcentb.async.changestream.ChangeStreamListener;
import com.zuunr.dcentb.async.changestream.HaltListenerException;
import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import com.zuunr.mongodb.MongoJsonDB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * A {@code Consumer<JsonObject>} suitable as a {@link ChangeStreamListener} {@code eventHandler} —
 * routes each raw change event to the {@link Topic} it belongs to (by source collection) and, for a
 * fan-out topic (more than one subscriber — see {@link Topic#isFanOut()}), also handles the
 * publish/fan-out mechanics via a single shared message-log collection. See
 * docs/async-tasks-processing.md §9.1 for the full design and §9.3 for how {@link TaskResult}
 * outcomes map to behavior.
 *
 * <h2>Direct-attach vs. fan-out</h2>
 * A topic with exactly one subscriber never touches the message-log collection at all — its
 * {@link TaskProcessor} is invoked directly on the raw source event. A topic with more than one
 * subscriber writes <em>one</em> message document (not one per subscriber) into the shared message-log
 * collection; because that collection is itself one of {@link ChangeStreamListener}'s
 * {@code watchedCollections}, the very same listener/cursor picks up that insert and this router
 * dispatches it to every subscriber — no second listener, no second leader election, no per-subscriber
 * offset tracking. All subscribers of one fan-out event are handled synchronously, in the same call
 * that observed the message-log insert, which is why no offset bookkeeping is needed: there is no
 * independent per-subscriber pace to track, they all advance in lockstep with the one shared cursor.
 * (An earlier draft of the design, in async-tasks-processing.md §9.1/§9.2, sketched a separate
 * per-subscriber offsets collection for this — that was solving a problem that doesn't actually exist
 * once fan-out is driven synchronously by a single listener; it added bookkeeping without adding
 * correctness, so it was dropped during implementation.)
 *
 * <h2>{@link TaskResult} handling</h2>
 * {@code SUCCESS}: nothing further. {@code RETRY}: retried exactly once, inline; if that also fails
 * (another {@code RETRY}, a thrown exception, or a null return), it escalates to {@code ERROR_QUEUE} —
 * confirmed, final policy, not configurable. {@code ERROR_QUEUE}: written to {@link ErrorQueueStore}
 * and considered handled — the listener's checkpoint still advances, so one struggling
 * topic/subscriber never blocks anything else. {@code BLOCK_ALL}: thrown onward as
 * {@link HaltListenerException}, which {@link ChangeStreamListener} handles by halting entirely.
 */
public final class TopicRouter implements Consumer<JsonObject> {

    private static final Logger LOG = LoggerFactory.getLogger(TopicRouter.class);

    private final String messageLogCollection;
    private final MongoJsonDB mongoJsonDB;
    private final ErrorQueueStore errorQueueStore;
    private final Map<String, List<Topic>> topicsBySourceCollection;
    private final Map<String, Topic> topicsByName;

    public TopicRouter(MongoJsonDB mongoJsonDB, String messageLogCollection, ErrorQueueStore errorQueueStore, List<Topic> topics) {
        this.mongoJsonDB = mongoJsonDB;
        this.messageLogCollection = messageLogCollection;
        this.errorQueueStore = errorQueueStore;

        Map<String, List<Topic>> bySource = new HashMap<>();
        Map<String, Topic> byName = new HashMap<>();
        for (Topic topic : topics) {
            bySource.computeIfAbsent(topic.getSourceCollection(), k -> new java.util.ArrayList<>()).add(topic);
            if (byName.putIfAbsent(topic.getName(), topic) != null) {
                throw new IllegalArgumentException("duplicate topic name: " + topic.getName());
            }
        }
        this.topicsBySourceCollection = Map.copyOf(bySource);
        this.topicsByName = Map.copyOf(byName);
    }

    /**
     * The exhaustive set of collections a {@link ChangeStreamListener} driving this router must be
     * constructed with — every topic's source collection, plus the shared message-log collection if
     * any topic fans out. See {@link ChangeStreamListener}'s Javadoc for why this must be exact.
     */
    public static Set<String> watchedCollectionsFor(List<Topic> topics, String messageLogCollection) {
        Set<String> collections = new HashSet<>();
        boolean anyFanOut = false;
        for (Topic topic : topics) {
            collections.add(topic.getSourceCollection());
            anyFanOut = anyFanOut || topic.isFanOut();
        }
        if (anyFanOut) {
            collections.add(messageLogCollection);
        }
        return collections;
    }

    /**
     * Re-invokes exactly one subscriber's {@link TaskProcessor} directly, bypassing the normal
     * routing-by-collection path — used by {@link ErrorQueueProcessor} to redeliver an item it
     * already knows the topic/subscriber for. A single attempt, no inline retry (unlike
     * {@link #dispatch}): the error-queue processor owns its own retry/backoff/dead-letter policy.
     */
    public TaskResult redeliver(String topicName, String subscriberName, JsonObject event) {
        Topic topic = topicsByName.get(topicName);
        if (topic == null) {
            return TaskResult.errorQueue("no such topic: " + topicName);
        }
        Subscriber subscriber = topic.getSubscribers().stream()
                .filter(s -> s.getName().equals(subscriberName))
                .findFirst()
                .orElse(null);
        if (subscriber == null) {
            return TaskResult.errorQueue("no such subscriber '" + subscriberName + "' in topic '" + topicName + "'");
        }
        return invokeSafely(subscriber, event);
    }

    @Override
    public void accept(JsonObject event) {
        String collection = event.get(JsonArray.of("ns", "coll")).getString();

        if (collection.equals(messageLogCollection)) {
            handleMessageLogEvent(event);
            return;
        }

        List<Topic> topics = topicsBySourceCollection.get(collection);
        if (topics == null) {
            LOG.warn("Received an event for collection '{}' with no matching topic — ignoring. " +
                    "This means watchedCollections included a collection with no corresponding topic; check the router's construction.", collection);
            return;
        }
        for (Topic topic : topics) {
            if (topic.isFanOut()) {
                publish(topic, event);
            } else {
                dispatch(topic, topic.getSubscribers().get(0), event);
            }
        }
    }

    private void handleMessageLogEvent(JsonObject event) {
        JsonValue fullDocumentValue = event.get("fullDocument");
        if (fullDocumentValue == null) {
            return; // e.g. a delete on the message-log collection — nothing to dispatch
        }
        JsonObject fullDocument = fullDocumentValue.getJsonObject();
        String topicName = fullDocument.get("topic").getString();
        Topic topic = topicsByName.get(topicName);
        if (topic == null) {
            LOG.warn("Message-log entry references unknown topic '{}' — ignoring", topicName);
            return;
        }
        JsonObject payload = fullDocument.get("payload").getJsonObject();
        for (Subscriber subscriber : topic.getSubscribers()) {
            dispatch(topic, subscriber, payload);
        }
    }

    private void publish(Topic topic, JsonObject event) {
        JsonObject message = JsonObject.EMPTY
                .put("topic", topic.getName())
                .put("payload", event)
                .put("createdAt", Instant.now().toString());

        JsonObject command = JsonObject.EMPTY.put("insert", JsonObject.EMPTY
                .put("collection", messageLogCollection)
                .put("documents", JsonArray.of(message)));

        mongoJsonDB.runCommand(command);
    }

    private void dispatch(Topic topic, Subscriber subscriber, JsonObject event) {
        TaskResult result = invokeSafely(subscriber, event);

        if (result.getStatus() == TaskResult.Status.RETRY) {
            LOG.warn("TaskProcessor for topic '{}' subscriber '{}' requested retry ({}) — retrying once inline",
                    topic.getName(), subscriber.getName(), result.getReason());
            TaskResult retryResult = invokeSafely(subscriber, event);
            result = retryResult.getStatus() == TaskResult.Status.RETRY
                    ? TaskResult.errorQueue("retry failed: " + retryResult.getReason(), retryResult.getCause())
                    : retryResult;
        }

        switch (result.getStatus()) {
            case SUCCESS:
                return;
            case ERROR_QUEUE:
                LOG.error("TaskProcessor for topic '{}' subscriber '{}' failed — moving to the error queue: {}",
                        topic.getName(), subscriber.getName(), result.getReason(), result.getCause());
                errorQueueStore.add(topic.getName(), subscriber.getName(), event, result);
                return;
            case BLOCK_ALL:
                LOG.error("TaskProcessor for topic '{}' subscriber '{}' requested BLOCK_ALL: {}",
                        topic.getName(), subscriber.getName(), result.getReason(), result.getCause());
                throw new HaltListenerException(
                        "BLOCK_ALL from topic '" + topic.getName() + "' subscriber '" + subscriber.getName() + "': " + result.getReason(),
                        result.getCause());
            default:
                throw new IllegalStateException("Unreachable: RETRY must have been resolved above, got " + result.getStatus());
        }
    }

    private TaskResult invokeSafely(Subscriber subscriber, JsonObject event) {
        try {
            TaskResult result = subscriber.getTaskProcessor().process(event);
            return result != null ? result : TaskResult.retry("TaskProcessor returned null");
        } catch (RuntimeException e) {
            return TaskResult.retry("TaskProcessor threw: " + e.getMessage(), e);
        }
    }
}
