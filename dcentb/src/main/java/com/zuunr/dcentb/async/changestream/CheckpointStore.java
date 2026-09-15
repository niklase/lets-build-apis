package com.zuunr.dcentb.async.changestream;

import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import com.zuunr.mongodb.MongoJsonDB;
import org.bson.BsonDocument;
import org.bson.BsonTimestamp;

import java.time.Instant;
import java.util.Optional;

/**
 * Persists one {@link Checkpoint} per {@code streamId} via {@link MongoJsonDB} — the resume token is
 * stored as its own opaque canonical-JSON string (never parsed or structurally modeled; MongoDB's own
 * guidance is to treat resume tokens as opaque), and {@code clusterTime} is stored with full precision
 * (seconds + increment) as plain numbers, alongside a human-readable ISO string purely for dashboards.
 */
public final class CheckpointStore {

    private final MongoJsonDB mongoJsonDB;
    private final String collection;
    private final String streamId;

    public CheckpointStore(MongoJsonDB mongoJsonDB, String collection, String streamId) {
        this.mongoJsonDB = mongoJsonDB;
        this.collection = collection;
        this.streamId = streamId;
    }

    public Optional<Checkpoint> load() {
        JsonObject command = JsonObject.EMPTY.put("find", JsonObject.EMPTY
                .put("collection", collection)
                .put("filter", JsonObject.EMPTY.put("_id", JsonArray.of(
                        JsonObject.EMPTY.put("$eq", streamId)))));

        JsonObject result = mongoJsonDB.runCommand(command);
        JsonArray firstBatch = result.get(JsonArray.of("cursor", "firstBatch")).getJsonArray();
        if (firstBatch.size() == 0) {
            return Optional.empty();
        }

        JsonObject doc = firstBatch.get(0).getJsonObject();
        BsonDocument resumeToken = BsonDocument.parse(doc.get("resumeTokenJson").getString());
        int seconds = doc.get("clusterTimeSeconds").getInteger();
        int increment = doc.get("clusterTimeIncrement").getInteger();
        return Optional.of(new Checkpoint(resumeToken, new BsonTimestamp(seconds, increment)));
    }

    public void save(BsonDocument resumeToken, BsonTimestamp clusterTime) {
        JsonObject query = JsonObject.EMPTY.put("_id", JsonArray.of(
                JsonObject.EMPTY.put("$eq", streamId)));

        JsonObject update = JsonObject.EMPTY.put("$set", JsonObject.EMPTY
                .put("resumeTokenJson", resumeToken.toJson())
                .put("clusterTimeSeconds", clusterTime.getTime())
                .put("clusterTimeIncrement", clusterTime.getInc())
                .put("clusterTimeIso", Instant.ofEpochSecond(clusterTime.getTime()).toString())
                .put("updatedAt", Instant.now().toString()));

        JsonObject command = JsonObject.EMPTY.put("findAndModify", JsonObject.EMPTY
                .put("collection", collection)
                .put("query", query)
                .put("update", update)
                .put("upsert", true)
                .put("writeConcern", JsonObject.EMPTY.put("w", "majority")));

        mongoJsonDB.runCommand(command);
    }
}
