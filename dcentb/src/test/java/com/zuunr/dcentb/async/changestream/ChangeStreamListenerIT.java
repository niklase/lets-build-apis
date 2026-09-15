package com.zuunr.dcentb.async.changestream;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.zuunr.dcentb.async.leaderelection.LeaderElector;
import com.zuunr.json.JsonObject;
import com.zuunr.mongodb.MongoJsonDB;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link ChangeStreamListener} against a real MongoDB replica set (same prerequisite as
 * LeaderElectorIT — see public/dcentb/README.md). Every test uses a fresh, random source collection
 * and streamId so tests never interfere with each other.
 */
class ChangeStreamListenerIT {

    private static final String CONNECTION = "mongodb://admin:adminpassword@localhost:27017/?authSource=admin";
    private static final MongoClient MONGO_CLIENT = MongoClients.create(CONNECTION);
    private static final MongoDatabase DATABASE = MONGO_CLIENT.getDatabase("changestreamlistenerit");
    private static final MongoJsonDB MONGO_JSON_DB = new MongoJsonDB(DATABASE);
    private static final String CHECKPOINT_COLLECTION = "async-checkpoints";
    private static final String LEASE_COLLECTION = "async-leader-lease";

    private String sourceCollection;
    private String streamId;
    private final List<ChangeStreamListener> listenersToStop = new ArrayList<>();

    @BeforeEach
    void setUp() {
        sourceCollection = "source-" + UUID.randomUUID().toString().replace("-", "");
        streamId = "stream-" + UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        listenersToStop.forEach(ChangeStreamListener::stop);
        listenersToStop.clear();
    }

    private ChangeStreamListener newListener(String instanceId, java.util.function.Consumer<JsonObject> handler) {
        LeaderElector elector = new LeaderElector(MONGO_JSON_DB, LEASE_COLLECTION, streamId, instanceId, Duration.ofSeconds(5));
        CheckpointStore checkpointStore = new CheckpointStore(MONGO_JSON_DB, CHECKPOINT_COLLECTION, streamId);
        ChangeStreamListener listener = new ChangeStreamListener(DATABASE, MONGO_JSON_DB, checkpointStore, elector,
                Duration.ofMillis(500), java.util.Set.of(sourceCollection), handler);
        listenersToStop.add(listener);
        return listener;
    }

    @Test
    void observesAnInsertOnTheWatchedCollection() throws InterruptedException {
        List<JsonObject> received = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        ChangeStreamListener listener = newListener("instance-A", event -> {
            received.add(event);
            latch.countDown();
        });
        listener.start();
        waitUntilWatching();

        DATABASE.getCollection(sourceCollection).insertOne(new Document("name", "Anna"));

        assertTrue(latch.await(10, TimeUnit.SECONDS), "expected the insert to be observed within 10s");
        assertEquals(1, received.size());
        JsonObject event = received.get(0);
        assertEquals("insert", event.get("operationType").getString());
        assertEquals(sourceCollection, event.get(com.zuunr.json.JsonArray.of("ns", "coll")).getString());
        assertEquals("Anna", event.get(com.zuunr.json.JsonArray.of("fullDocument", "name")).getString());
    }

    @Test
    void checkpointAdvancesSoARestartedListenerDoesNotReplayAlreadyProcessedEvents() throws InterruptedException {
        List<JsonObject> firstRunEvents = new CopyOnWriteArrayList<>();
        CountDownLatch firstLatch = new CountDownLatch(1);

        ChangeStreamListener first = newListener("instance-A", event -> {
            firstRunEvents.add(event);
            firstLatch.countDown();
        });
        first.start();
        waitUntilWatching();

        DATABASE.getCollection(sourceCollection).insertOne(new Document("name", "Anna"));
        assertTrue(firstLatch.await(10, TimeUnit.SECONDS));
        waitForCheckpointToSettle();
        first.stop();

        // A second listener (simulating a restart) must resume from the checkpoint, not replay Anna.
        List<JsonObject> secondRunEvents = new CopyOnWriteArrayList<>();
        CountDownLatch secondLatch = new CountDownLatch(1);
        ChangeStreamListener second = newListener("instance-A-restarted", event -> {
            secondRunEvents.add(event);
            secondLatch.countDown();
        });
        second.start();
        waitUntilWatching();

        DATABASE.getCollection(sourceCollection).insertOne(new Document("name", "Robert"));
        assertTrue(secondLatch.await(10, TimeUnit.SECONDS));

        assertEquals(1, secondRunEvents.size(), "the restarted listener must only see the new event, not replay Anna");
        assertEquals("Robert", secondRunEvents.get(0).get(com.zuunr.json.JsonArray.of("fullDocument", "name")).getString());
    }

    @Test
    void secondListenerTakesOverAfterFirstStops() throws InterruptedException {
        List<String> namesA = new CopyOnWriteArrayList<>();
        CountDownLatch latchA = new CountDownLatch(1);
        ChangeStreamListener listenerA = newListener("instance-A", event -> {
            namesA.add(event.get(com.zuunr.json.JsonArray.of("fullDocument", "name")).getString());
            latchA.countDown();
        });
        listenerA.start();
        waitUntilWatching();

        DATABASE.getCollection(sourceCollection).insertOne(new Document("name", "Anna"));
        assertTrue(latchA.await(10, TimeUnit.SECONDS));
        waitForCheckpointToSettle();
        listenerA.stop(); // releases leadership immediately

        List<String> namesB = new CopyOnWriteArrayList<>();
        CountDownLatch latchB = new CountDownLatch(1);
        ChangeStreamListener listenerB = newListener("instance-B", event -> {
            namesB.add(event.get(com.zuunr.json.JsonArray.of("fullDocument", "name")).getString());
            latchB.countDown();
        });
        listenerB.start();
        waitUntilWatching();

        DATABASE.getCollection(sourceCollection).insertOne(new Document("name", "Robert"));
        assertTrue(latchB.await(10, TimeUnit.SECONDS), "instance-B must take over leadership and observe the new event");
        assertEquals(List.of("Robert"), namesB);
    }

    /**
     * There's no direct "is the cursor open yet" signal exposed, and starting the listener is
     * asynchronous (it runs its election + cursor-open on a background thread). A short, fixed wait is
     * simplest and reliable in practice: leader election against a fresh lease is fast (well under
     * 100ms), and change stream cursors only observe events that occur *after* they open, so the test
     * writes (which happen after this wait) are always racing a cursor that is already live.
     */
    private void waitUntilWatching() throws InterruptedException {
        Thread.sleep(500);
    }

    /**
     * A test handler's CountDownLatch fires the instant the handler runs — but the listener's
     * background thread still has its own fencing re-check and checkpoint write left to do after
     * that (see ChangeStreamListener.consumeUntilLeadershipLostOrStopped). Calling stop() the same
     * instant the latch fires races that in-flight work: stop()'s interrupt can land mid-fencing-check,
     * which correctly (per the documented fail-safe philosophy — never assume leadership on
     * uncertainty) resolves to "not leader" and skips the checkpoint write. That's not a bug — it's
     * exactly the at-least-once/idempotent-redelivery contract this whole system is built on — but it
     * would make tests that specifically assert "the checkpoint prevented a replay" flaky for reasons
     * unrelated to what they're testing. A short pause here lets that trailing work finish under
     * normal (non-interrupted) conditions before a test deliberately stops the listener.
     */
    private void waitForCheckpointToSettle() throws InterruptedException {
        Thread.sleep(300);
    }
}
