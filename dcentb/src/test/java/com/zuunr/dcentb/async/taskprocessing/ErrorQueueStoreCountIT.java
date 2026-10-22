package com.zuunr.dcentb.async.taskprocessing;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.zuunr.mongodb.MongoJsonDB;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises {@link ErrorQueueStore#count()}/{@link ErrorQueueStore#deadLetterCount} against a real
 * MongoDB (see public/dcentb/README.md, "Start MongoDB"). Every test uses fresh, random collection
 * names so tests never interfere with each other.
 */
class ErrorQueueStoreCountIT {

    private static final String CONNECTION = "mongodb://admin:adminpassword@localhost:27017/?authSource=admin";
    private static final MongoClient MONGO_CLIENT = MongoClients.create(CONNECTION);
    private static final MongoDatabase DATABASE = MONGO_CLIENT.getDatabase("errorqueuestorecountit");
    private static final MongoJsonDB MONGO_JSON_DB = new MongoJsonDB(DATABASE);

    private String collection;
    private String deadLetterCollection;
    private ErrorQueueStore store;

    @BeforeEach
    void setUp() {
        collection = "error-queue-" + UUID.randomUUID();
        deadLetterCollection = "dead-letters-" + UUID.randomUUID();
        store = new ErrorQueueStore(MONGO_JSON_DB, collection);
    }

    @Test
    void countIsZeroOnAnEmptyCollection() {
        assertEquals(0L, store.count());
    }

    @Test
    void countReflectsEveryAddedItemRegardlessOfDueTime() {
        store.add("topic-a", "subscriber-a", com.zuunr.json.JsonObject.EMPTY, TaskResult.retry("test"));
        store.add("topic-a", "subscriber-b", com.zuunr.json.JsonObject.EMPTY, TaskResult.retry("test"));

        assertEquals(2L, store.count());
    }

    @Test
    void deadLetterCountIsZeroBeforeAnythingIsMoved() {
        assertEquals(0L, store.deadLetterCount(deadLetterCollection));
    }

    @Test
    void deadLetterCountReflectsItemsMovedThere() {
        store.add("topic-a", "subscriber-a", com.zuunr.json.JsonObject.EMPTY, TaskResult.retry("test"));
        com.zuunr.json.JsonObject item = store.fetchDueBatch(10).get(0);

        store.moveToDeadLetter(item, TaskResult.errorQueue("exhausted"), deadLetterCollection);

        assertEquals(0L, store.count());
        assertEquals(1L, store.deadLetterCount(deadLetterCollection));
    }
}
