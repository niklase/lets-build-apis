package com.zuunr.dcentb.async.taskprocessing;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.zuunr.dcentb.async.leaderelection.LeaderElector;
import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.mongodb.MongoJsonDB;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link ErrorQueueProcessor} against a real MongoDB. Leadership is established with a
 * single manual {@code tryAcquireOrRenew()} call rather than a running ChangeStreamListener — see
 * the class Javadoc's point about this processor never renewing on its own; a lease duration well
 * longer than any of these tests need is enough to avoid needing a renewer thread here.
 */
class ErrorQueueProcessorIT {

    private static final String CONNECTION = "mongodb://admin:adminpassword@localhost:27017/?authSource=admin";
    private static final MongoClient MONGO_CLIENT = MongoClients.create(CONNECTION);
    private static final MongoDatabase DATABASE = MONGO_CLIENT.getDatabase("errorqueueprocessorit");
    private static final MongoJsonDB MONGO_JSON_DB = new MongoJsonDB(DATABASE);
    private static final String LEASE_COLLECTION = "async-leader-lease";
    private static final String ERROR_QUEUE_COLLECTION = "async-processing-errors";
    private static final String DEAD_LETTER_COLLECTION = "async-processing-dead-letters";

    private String topicName;
    private ErrorQueueStore errorQueueStore;
    private ErrorQueueProcessor processor;

    @BeforeEach
    void setUp() {
        topicName = "topic-" + UUID.randomUUID();
        errorQueueStore = new ErrorQueueStore(MONGO_JSON_DB, ERROR_QUEUE_COLLECTION);
    }

    @AfterEach
    void tearDown() {
        if (processor != null) {
            processor.stop();
        }
    }

    private LeaderElector acquiredLeader(String streamId) {
        LeaderElector elector = new LeaderElector(MONGO_JSON_DB, LEASE_COLLECTION, streamId, "instance-" + UUID.randomUUID(), Duration.ofSeconds(30));
        assertTrue(elector.tryAcquireOrRenew().isLeader());
        return elector;
    }

    private static final class ScriptedTaskProcessor implements TaskProcessor {
        final List<TaskResult> script;
        final AtomicInteger callCount = new AtomicInteger();

        ScriptedTaskProcessor(TaskResult... script) {
            this.script = List.of(script);
        }

        @Override
        public TaskResult process(JsonObject event) {
            int index = Math.min(callCount.getAndIncrement(), script.size() - 1);
            return script.get(index);
        }
    }

    @Test
    void succeedingRedeliveryRemovesTheItem() throws InterruptedException {
        ScriptedTaskProcessor taskProcessor = new ScriptedTaskProcessor(TaskResult.success());
        Topic topic = new Topic(topicName, "unused-source", List.of(new Subscriber("only", taskProcessor)));
        TopicRouter router = new TopicRouter(MONGO_JSON_DB, "async-topic-messages", errorQueueStore, List.of(topic));

        errorQueueStore.add(topicName, "only", JsonObject.EMPTY.put("name", "Anna"), TaskResult.errorQueue("initial failure"));

        LeaderElector elector = acquiredLeader("stream-" + UUID.randomUUID());
        processor = new ErrorQueueProcessor(errorQueueStore, elector, router, DEAD_LETTER_COLLECTION, 5,
                Duration.ofMillis(100), Duration.ofMillis(50), Duration.ofSeconds(1));
        processor.start();

        waitUntil(() -> countErrorQueueDocs(topicName) == 0, Duration.ofSeconds(5));
        assertEquals(1, taskProcessor.callCount.get());
        assertEquals(0, countDeadLetterDocs(topicName));
    }

    @Test
    void repeatedFailureEventuallyMovesToDeadLetterAfterMaxRequeues() {
        ScriptedTaskProcessor taskProcessor = new ScriptedTaskProcessor(TaskResult.errorQueue("still broken"));
        Topic topic = new Topic(topicName, "unused-source", List.of(new Subscriber("only", taskProcessor)));
        TopicRouter router = new TopicRouter(MONGO_JSON_DB, "async-topic-messages", errorQueueStore, List.of(topic));

        errorQueueStore.add(topicName, "only", JsonObject.EMPTY.put("name", "Anna"), TaskResult.errorQueue("initial failure"));

        LeaderElector elector = acquiredLeader("stream-" + UUID.randomUUID());
        int maxRequeues = 3;
        processor = new ErrorQueueProcessor(errorQueueStore, elector, router, DEAD_LETTER_COLLECTION, maxRequeues,
                Duration.ofMillis(50), Duration.ofMillis(10), Duration.ofMillis(50));
        processor.start();

        waitUntil(() -> countDeadLetterDocs(topicName) == 1, Duration.ofSeconds(10));
        assertEquals(0, countErrorQueueDocs(topicName), "must be removed from the error queue once dead-lettered");
        // errorQueueStore.add() already seeds attempts=1 (the live failure that queued it in the first
        // place); maxRequeues counts total attempts, so exactly maxRequeues-1 *background* redeliveries
        // happen here before the item is dead-lettered.
        assertEquals(maxRequeues - 1, taskProcessor.callCount.get());

        JsonObject deadLetterDoc = findOneDeadLetterDoc(topicName);
        assertEquals("Anna", deadLetterDoc.get(JsonArray.of("event", "name")).getString());
        assertEquals("still broken", deadLetterDoc.get("finalReason").getString());
    }

    @Test
    void doesNothingWithoutLeadership() throws InterruptedException {
        ScriptedTaskProcessor taskProcessor = new ScriptedTaskProcessor(TaskResult.success());
        Topic topic = new Topic(topicName, "unused-source", List.of(new Subscriber("only", taskProcessor)));
        TopicRouter router = new TopicRouter(MONGO_JSON_DB, "async-topic-messages", errorQueueStore, List.of(topic));

        errorQueueStore.add(topicName, "only", JsonObject.EMPTY.put("name", "Anna"), TaskResult.errorQueue("initial failure"));

        // Never acquired — currentLease stays notLeader(), isStillLeader() always false.
        LeaderElector elector = new LeaderElector(MONGO_JSON_DB, LEASE_COLLECTION, "stream-" + UUID.randomUUID(), "instance-idle", Duration.ofSeconds(30));
        processor = new ErrorQueueProcessor(errorQueueStore, elector, router, DEAD_LETTER_COLLECTION, 5,
                Duration.ofMillis(50), Duration.ofMillis(50), Duration.ofSeconds(1));
        processor.start();

        Thread.sleep(500);

        assertEquals(0, taskProcessor.callCount.get());
        assertEquals(1, countErrorQueueDocs(topicName));
    }

    @Test
    void blockAllHaltsTheProcessorWithoutRemovingTheItem() throws InterruptedException {
        ScriptedTaskProcessor taskProcessor = new ScriptedTaskProcessor(TaskResult.blockAll("something is very wrong"));
        Topic topic = new Topic(topicName, "unused-source", List.of(new Subscriber("only", taskProcessor)));
        TopicRouter router = new TopicRouter(MONGO_JSON_DB, "async-topic-messages", errorQueueStore, List.of(topic));

        errorQueueStore.add(topicName, "only", JsonObject.EMPTY.put("name", "Anna"), TaskResult.errorQueue("initial failure"));

        LeaderElector elector = acquiredLeader("stream-" + UUID.randomUUID());
        processor = new ErrorQueueProcessor(errorQueueStore, elector, router, DEAD_LETTER_COLLECTION, 5,
                Duration.ofMillis(50), Duration.ofMillis(50), Duration.ofSeconds(1));
        processor.start();

        waitUntil(() -> taskProcessor.callCount.get() >= 1, Duration.ofSeconds(5));
        Thread.sleep(300); // give it a chance to (wrongly) keep going, if it were going to

        assertEquals(1, taskProcessor.callCount.get(), "must not keep redelivering after BLOCK_ALL");
        assertEquals(1, countErrorQueueDocs(topicName), "the item must stay in the error queue, neither deleted nor dead-lettered");
    }

    private void waitUntil(java.util.function.BooleanSupplier condition, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        assertTrue(condition.getAsBoolean(), "condition not met within " + timeout);
    }

    private long countErrorQueueDocs(String topic) {
        return countDocsForTopic(ERROR_QUEUE_COLLECTION, topic);
    }

    private long countDeadLetterDocs(String topic) {
        return countDocsForTopic(DEAD_LETTER_COLLECTION, topic);
    }

    private long countDocsForTopic(String collection, String topic) {
        JsonObject command = JsonObject.EMPTY.put("find", JsonObject.EMPTY
                .put("collection", collection)
                .put("filter", JsonObject.EMPTY.put("topic", JsonArray.of(
                        JsonObject.EMPTY.put("$eq", topic)))));
        JsonObject result = MONGO_JSON_DB.runCommand(command);
        return result.get(JsonArray.of("cursor", "firstBatch")).getJsonArray().size();
    }

    private JsonObject findOneDeadLetterDoc(String topic) {
        JsonObject command = JsonObject.EMPTY.put("find", JsonObject.EMPTY
                .put("collection", DEAD_LETTER_COLLECTION)
                .put("filter", JsonObject.EMPTY.put("topic", JsonArray.of(
                        JsonObject.EMPTY.put("$eq", topic)))));
        JsonObject result = MONGO_JSON_DB.runCommand(command);
        JsonArray firstBatch = result.get(JsonArray.of("cursor", "firstBatch")).getJsonArray();
        assertEquals(1, firstBatch.size());
        return firstBatch.get(0).getJsonObject();
    }
}
