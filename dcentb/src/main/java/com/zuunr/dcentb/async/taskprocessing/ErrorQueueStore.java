package com.zuunr.dcentb.async.taskprocessing;

import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import com.zuunr.mongodb.MongoJsonDB;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Read/write access to one shared collection of failed items (tagged with {@code topic}/
 * {@code subscriber} — see docs/async-tasks-processing.md §2.6 for why shared rather than per-topic),
 * via {@link MongoJsonDB}. Writing (from live {@link TopicRouter} dispatch) and reading/rescheduling
 * (from {@link ErrorQueueProcessor}'s background poll) are both here since they share one document
 * shape and one collection.
 *
 * <p>{@code _id} is an explicit random UUID string, not an auto-generated ObjectId — this keeps every
 * later reference to a specific document (reschedule, delete, move-to-dead-letter) a plain string
 * comparison through the existing JSON query DSL, avoiding ObjectId's special wire representation
 * (see {@code MongoJsonDBIT}'s {@code {"_id": {"ObjectId": ...}}} convention) for a case that doesn't
 * need it.
 */
public final class ErrorQueueStore {

    private final MongoJsonDB mongoJsonDB;
    private final String collection;

    public ErrorQueueStore(MongoJsonDB mongoJsonDB, String collection) {
        this.mongoJsonDB = mongoJsonDB;
        this.collection = collection;
    }

    /** Called from live {@link TopicRouter} dispatch. Eligible for reprocessing immediately. */
    public void add(String topic, String subscriber, JsonObject event, TaskResult result) {
        Instant now = Instant.now();
        JsonObject doc = JsonObject.EMPTY
                .put("_id", UUID.randomUUID().toString())
                .put("topic", topic)
                .put("subscriber", subscriber)
                .put("event", event)
                .put("reason", result.getReason() == null ? JsonValue.NULL : JsonValue.of(result.getReason()))
                .put("attempts", 1)
                .put("createdAt", now.toString())
                .put("nextAttemptAt", now.toString());

        JsonObject command = JsonObject.EMPTY.put("insert", JsonObject.EMPTY
                .put("collection", collection)
                .put("documents", JsonArray.of(doc)));

        mongoJsonDB.runCommand(command);
    }

    /** Oldest-first, only items whose {@code nextAttemptAt} has passed. */
    public List<JsonObject> fetchDueBatch(int limit) {
        JsonObject command = JsonObject.EMPTY.put("find", JsonObject.EMPTY
                .put("collection", collection)
                .put("filter", JsonObject.EMPTY.put("nextAttemptAt", JsonArray.of(
                        JsonObject.EMPTY.put("$lte", Instant.now().toString()))))
                .put("sort", JsonArray.of(JsonObject.EMPTY.put("createdAt", 1)))
                .put("limit", limit));

        JsonObject result = mongoJsonDB.runCommand(command);
        JsonArray firstBatch = result.get(JsonArray.of("cursor", "firstBatch")).getJsonArray();
        List<JsonObject> items = new ArrayList<>(firstBatch.size());
        for (int i = 0; i < firstBatch.size(); i++) {
            items.add(firstBatch.get(i).getJsonObject());
        }
        return items;
    }

    public void delete(String id) {
        JsonObject command = JsonObject.EMPTY.put("delete", JsonObject.EMPTY
                .put("collection", collection)
                .put("deletes", JsonArray.of(JsonObject.EMPTY
                        .put("q", JsonObject.EMPTY.put("_id", JsonArray.of(
                                JsonObject.EMPTY.put("$eq", id))))
                        .put("limit", 1))));
        mongoJsonDB.runCommand(command);
    }

    public void rescheduleAfterFailure(String id, int newAttemptCount, Instant nextAttemptAt, TaskResult result) {
        JsonObject update = JsonObject.EMPTY.put("$set", JsonObject.EMPTY
                .put("attempts", newAttemptCount)
                .put("nextAttemptAt", nextAttemptAt.toString())
                .put("reason", result.getReason() == null ? JsonValue.NULL : JsonValue.of(result.getReason()))
                .put("lastAttemptAt", Instant.now().toString()));

        JsonObject command = JsonObject.EMPTY.put("update", JsonObject.EMPTY
                .put("collection", collection)
                .put("updates", JsonArray.of(JsonObject.EMPTY
                        .put("q", JsonObject.EMPTY.put("_id", JsonArray.of(
                                JsonObject.EMPTY.put("$eq", id))))
                        .put("u", update))));
        mongoJsonDB.runCommand(command);
    }

    public void moveToDeadLetter(JsonObject item, TaskResult finalResult, String deadLetterCollection) {
        JsonObject deadLetterDoc = item
                .put("finalReason", finalResult.getReason() == null ? JsonValue.NULL : JsonValue.of(finalResult.getReason()))
                .put("movedToDeadLetterAt", Instant.now().toString());

        JsonObject insertCommand = JsonObject.EMPTY.put("insert", JsonObject.EMPTY
                .put("collection", deadLetterCollection)
                .put("documents", JsonArray.of(deadLetterDoc)));
        mongoJsonDB.runCommand(insertCommand);

        delete(item.get("_id").getString());
    }
}
