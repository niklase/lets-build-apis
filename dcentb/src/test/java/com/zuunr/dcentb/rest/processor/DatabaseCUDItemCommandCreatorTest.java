package com.zuunr.dcentb.rest.processor;

import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers the two mongoCommand shapes DatabaseCUDItemCommandCreator produces: an "update"
 * (upsert) for PUT/POST/PATCH, and a "delete" for DELETE - in particular that both are
 * optimistic-concurrency guarded by conditioning on meta.etag, not just _id, so a write
 * against a document that moved on after this request read it as currentState/mongoItem
 * matches nothing rather than silently applying against the wrong version.
 */
class DatabaseCUDItemCommandCreatorTest {

    private static JsonValue configFor(String collection) {
        return JsonObject.EMPTY
                .put("operation", JsonObject.EMPTY
                        .put("x-dcentb", JsonObject.EMPTY
                                .put("mongodb", JsonObject.EMPTY.put("collection", collection))))
                .jsonValue();
    }

    @Test
    void updateCommandConditionsOnMongoItemsEtagAndUpserts() {
        DatabaseCUDItemCommandCreator processor = new DatabaseCUDItemCommandCreator(configFor("things"));
        JsonObject mongoItem = JsonObject.EMPTY
                .put("_id", "thing-1")
                .put("name", "Widget")
                .put("meta", JsonObject.EMPTY.put("etag", "etag-v1"));
        JsonObject requestContext = JsonObject.EMPTY.put("mongoItem", mongoItem);

        JsonObject result = processor.process(requestContext);

        JsonObject expected = JsonObject.EMPTY.put("update", JsonObject.EMPTY
                .put("collection", "things")
                .put("updates", JsonArray.of(
                        JsonObject.EMPTY
                                .put("q", JsonObject.EMPTY
                                        .put("$and", JsonArray.of(
                                                JsonObject.EMPTY.put("_id", JsonArray.of(
                                                        JsonObject.EMPTY.put("$eq", "thing-1"))),
                                                JsonObject.EMPTY.put("meta.etag", JsonArray.of(
                                                        JsonObject.EMPTY.put("$eq", "etag-v1"))))))
                                .put("u", mongoItem)
                                .put("upsert", true))));

        assertEquals(expected, result.get("mongoCommand").getJsonObject());
    }

    @Test
    void deleteCommandConditionsOnCurrentStatesEtag() {
        DatabaseCUDItemCommandCreator processor = new DatabaseCUDItemCommandCreator(configFor("things"));
        JsonObject currentState = JsonObject.EMPTY
                .put("name", "Widget")
                .put("meta", JsonObject.EMPTY.put("id", "thing-1").put("etag", "etag-v1"));
        JsonObject requestContext = JsonObject.EMPTY
                .put("itemId", "thing-1")
                .put("currentState", currentState);

        JsonObject result = processor.process(requestContext);

        JsonObject expected = JsonObject.EMPTY.put("delete", JsonObject.EMPTY
                .put("collection", "things")
                .put("deletes", JsonArray.of(
                        JsonObject.EMPTY
                                .put("q", JsonObject.EMPTY
                                        .put("$and", JsonArray.of(
                                                JsonObject.EMPTY.put("_id", JsonArray.of(
                                                        JsonObject.EMPTY.put("$eq", "thing-1"))),
                                                JsonObject.EMPTY.put("meta.etag", JsonArray.of(
                                                        JsonObject.EMPTY.put("$eq", "etag-v1"))))))
                                .put("limit", 1))));

        assertEquals(expected, result.get("mongoCommand").getJsonObject());
    }
}
