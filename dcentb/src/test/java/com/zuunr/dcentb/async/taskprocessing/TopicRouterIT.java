package com.zuunr.dcentb.async.taskprocessing;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.zuunr.dcentb.async.changestream.HaltListenerException;
import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.mongodb.MongoJsonDB;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link TopicRouter} against a real MongoDB (for the message-log and error-queue writes)
 * — no {@link com.zuunr.dcentb.async.changestream.ChangeStreamListener} involved; events are
 * hand-built to match the JsonObject shape it would hand to an eventHandler, per
 * docs/change-stream-listener.md.
 */
class TopicRouterIT {

    private static final String CONNECTION = "mongodb://admin:adminpassword@localhost:27017/?authSource=admin";
    private static final MongoClient MONGO_CLIENT = MongoClients.create(CONNECTION);
    private static final MongoDatabase DATABASE = MONGO_CLIENT.getDatabase("topicrouterit");
    private static final MongoJsonDB MONGO_JSON_DB = new MongoJsonDB(DATABASE);
    private static final String MESSAGE_LOG_COLLECTION = "async-topic-messages";
    private static final String ERROR_QUEUE_COLLECTION = "async-processing-errors";

    private String sourceCollection;
    private String topicName;

    @BeforeEach
    void setUp() {
        sourceCollection = "source-" + UUID.randomUUID().toString().replace("-", "");
        // Must be unique per test, same as sourceCollection: the error-queue and message-log
        // collections are shared across all tests (by design — see ErrorQueueStore's Javadoc), so a
        // reused topic name would let one test's assertions see documents another test left behind.
        topicName = "topic-" + UUID.randomUUID();
    }

    private JsonObject insertEvent(String collection, JsonObject fullDocument) {
        return JsonObject.EMPTY
                .put("operationType", "insert")
                .put("ns", JsonObject.EMPTY.put("db", "topicrouterit").put("coll", collection))
                .put("fullDocument", fullDocument);
    }

    /** Returns a fixed result on each successive call, repeating the last one once the list is exhausted. */
    private static final class ScriptedTaskProcessor implements TaskProcessor {
        final List<TaskResult> script;
        final AtomicInteger callCount = new AtomicInteger();
        final List<JsonObject> receivedEvents = new java.util.concurrent.CopyOnWriteArrayList<>();

        ScriptedTaskProcessor(TaskResult... script) {
            this.script = List.of(script);
        }

        @Override
        public TaskResult process(JsonObject event) {
            receivedEvents.add(event);
            int index = Math.min(callCount.getAndIncrement(), script.size() - 1);
            return script.get(index);
        }
    }

    @Test
    void directAttachTopicInvokesTaskProcessorOnceOnSuccess() {
        ScriptedTaskProcessor processor = new ScriptedTaskProcessor(TaskResult.success());
        Topic topic = new Topic(topicName, sourceCollection, List.of(new Subscriber("only", processor)));
        TopicRouter router = new TopicRouter(MONGO_JSON_DB, MESSAGE_LOG_COLLECTION,
                new ErrorQueueStore(MONGO_JSON_DB, ERROR_QUEUE_COLLECTION), List.of(topic));

        JsonObject event = insertEvent(sourceCollection, JsonObject.EMPTY.put("name", "Anna"));
        router.accept(event);

        assertEquals(1, processor.callCount.get());
        assertEquals("Anna", processor.receivedEvents.get(0).get(JsonArray.of("fullDocument", "name")).getString());
    }

    @Test
    void retryThenSuccessDoesNotReachTheErrorQueue() {
        ScriptedTaskProcessor processor = new ScriptedTaskProcessor(TaskResult.retry("transient"), TaskResult.success());
        Topic topic = new Topic(topicName, sourceCollection, List.of(new Subscriber("only", processor)));
        TopicRouter router = new TopicRouter(MONGO_JSON_DB, MESSAGE_LOG_COLLECTION,
                new ErrorQueueStore(MONGO_JSON_DB, ERROR_QUEUE_COLLECTION), List.of(topic));

        router.accept(insertEvent(sourceCollection, JsonObject.EMPTY.put("name", "Anna")));

        assertEquals(2, processor.callCount.get());
        assertEquals(0, countErrorQueueDocsForTopic(topicName));
    }

    @Test
    void retryThatFailsAgainEscalatesToTheErrorQueueExactlyOnce() {
        ScriptedTaskProcessor processor = new ScriptedTaskProcessor(TaskResult.retry("first failure"), TaskResult.retry("second failure"));
        Topic topic = new Topic(topicName, sourceCollection, List.of(new Subscriber("only", processor)));
        TopicRouter router = new TopicRouter(MONGO_JSON_DB, MESSAGE_LOG_COLLECTION,
                new ErrorQueueStore(MONGO_JSON_DB, ERROR_QUEUE_COLLECTION), List.of(topic));

        router.accept(insertEvent(sourceCollection, JsonObject.EMPTY.put("name", "Anna")));

        assertEquals(2, processor.callCount.get(), "exactly one inline retry, not more");
        assertEquals(1, countErrorQueueDocsForTopic(topicName));
    }

    @Test
    void anExceptionFromTheTaskProcessorIsTreatedTheSameAsRetry() {
        TaskProcessor throwing = event -> {
            throw new RuntimeException("boom");
        };
        Topic topic = new Topic(topicName, sourceCollection, List.of(new Subscriber("only", throwing)));
        TopicRouter router = new TopicRouter(MONGO_JSON_DB, MESSAGE_LOG_COLLECTION,
                new ErrorQueueStore(MONGO_JSON_DB, ERROR_QUEUE_COLLECTION), List.of(topic));

        router.accept(insertEvent(sourceCollection, JsonObject.EMPTY.put("name", "Anna")));

        assertEquals(1, countErrorQueueDocsForTopic(topicName), "should have retried once inline then gone to the error queue, not propagated");
    }

    @Test
    void explicitErrorQueueResultSkipsTheInlineRetry() {
        ScriptedTaskProcessor processor = new ScriptedTaskProcessor(TaskResult.errorQueue("permanently broken"));
        Topic topic = new Topic(topicName, sourceCollection, List.of(new Subscriber("only", processor)));
        TopicRouter router = new TopicRouter(MONGO_JSON_DB, MESSAGE_LOG_COLLECTION,
                new ErrorQueueStore(MONGO_JSON_DB, ERROR_QUEUE_COLLECTION), List.of(topic));

        router.accept(insertEvent(sourceCollection, JsonObject.EMPTY.put("name", "Anna")));

        assertEquals(1, processor.callCount.get(), "ERROR_QUEUE is not RETRY — no inline retry attempt");
        assertEquals(1, countErrorQueueDocsForTopic(topicName));
    }

    @Test
    void blockAllThrowsHaltListenerException() {
        ScriptedTaskProcessor processor = new ScriptedTaskProcessor(TaskResult.blockAll("something is very wrong"));
        Topic topic = new Topic(topicName, sourceCollection, List.of(new Subscriber("only", processor)));
        TopicRouter router = new TopicRouter(MONGO_JSON_DB, MESSAGE_LOG_COLLECTION,
                new ErrorQueueStore(MONGO_JSON_DB, ERROR_QUEUE_COLLECTION), List.of(topic));

        HaltListenerException e = assertThrows(HaltListenerException.class, () ->
                router.accept(insertEvent(sourceCollection, JsonObject.EMPTY.put("name", "Anna"))));
        assertTrue(e.getMessage().contains(topicName));
        assertTrue(e.getMessage().contains("something is very wrong"));
    }

    @Test
    void unknownSourceCollectionEventIsIgnoredNotThrown() {
        TopicRouter router = new TopicRouter(MONGO_JSON_DB, MESSAGE_LOG_COLLECTION,
                new ErrorQueueStore(MONGO_JSON_DB, ERROR_QUEUE_COLLECTION), List.of());

        router.accept(insertEvent("some-unrelated-collection", JsonObject.EMPTY));
        // no exception is the assertion
    }

    @Test
    void fanOutTopicPublishesOneMessageAndDispatchingItReachesBothSubscribers() {
        ScriptedTaskProcessor subscriberA = new ScriptedTaskProcessor(TaskResult.success());
        ScriptedTaskProcessor subscriberB = new ScriptedTaskProcessor(TaskResult.success());
        Topic topic = new Topic(topicName, sourceCollection, List.of(
                new Subscriber("emailNotifier", subscriberA),
                new Subscriber("billingSync", subscriberB)));
        TopicRouter router = new TopicRouter(MONGO_JSON_DB, MESSAGE_LOG_COLLECTION,
                new ErrorQueueStore(MONGO_JSON_DB, ERROR_QUEUE_COLLECTION), List.of(topic));

        assertTrue(topic.isFanOut());
        assertEquals(Set.of(sourceCollection, MESSAGE_LOG_COLLECTION), TopicRouter.watchedCollectionsFor(List.of(topic), MESSAGE_LOG_COLLECTION));

        JsonObject sourceEvent = insertEvent(sourceCollection, JsonObject.EMPTY.put("name", "Anna"));
        router.accept(sourceEvent);

        // Neither subscriber runs off the raw source event directly — only via the message log.
        assertEquals(0, subscriberA.callCount.get());
        assertEquals(0, subscriberB.callCount.get());

        JsonObject messageLogDoc = findOneMessageLogDocForTopic(topicName);
        assertEquals(topicName, messageLogDoc.get("topic").getString());
        assertEquals("Anna", messageLogDoc.get(JsonArray.of("payload", "fullDocument", "name")).getString());

        // Simulate the listener observing that single insert into the message-log collection.
        router.accept(insertEvent(MESSAGE_LOG_COLLECTION, messageLogDoc));

        assertEquals(1, subscriberA.callCount.get());
        assertEquals(1, subscriberB.callCount.get());
        assertEquals("Anna", subscriberA.receivedEvents.get(0).get(JsonArray.of("fullDocument", "name")).getString());
        assertEquals("Anna", subscriberB.receivedEvents.get(0).get(JsonArray.of("fullDocument", "name")).getString());
    }

    @Test
    void watchedCollectionsForOmitsMessageLogWhenNoTopicFansOut() {
        Topic single = new Topic(topicName, sourceCollection, List.of(new Subscriber("only", e -> TaskResult.success())));
        assertEquals(Set.of(sourceCollection), TopicRouter.watchedCollectionsFor(List.of(single), MESSAGE_LOG_COLLECTION));
    }

    private long countErrorQueueDocsForTopic(String topicName) {
        JsonObject command = JsonObject.EMPTY.put("find", JsonObject.EMPTY
                .put("collection", ERROR_QUEUE_COLLECTION)
                .put("filter", JsonObject.EMPTY.put("topic", JsonArray.of(
                        JsonObject.EMPTY.put("$eq", topicName)))));
        JsonObject result = MONGO_JSON_DB.runCommand(command);
        return result.get(JsonArray.of("cursor", "firstBatch")).getJsonArray().size();
    }

    private JsonObject findOneMessageLogDocForTopic(String topicName) {
        JsonObject command = JsonObject.EMPTY.put("find", JsonObject.EMPTY
                .put("collection", MESSAGE_LOG_COLLECTION)
                .put("filter", JsonObject.EMPTY.put("topic", JsonArray.of(
                        JsonObject.EMPTY.put("$eq", topicName)))));
        JsonObject result = MONGO_JSON_DB.runCommand(command);
        JsonArray firstBatch = result.get(JsonArray.of("cursor", "firstBatch")).getJsonArray();
        assertEquals(1, firstBatch.size(), "expected exactly one message-log document for topic " + topicName);
        return firstBatch.get(0).getJsonObject();
    }
}
