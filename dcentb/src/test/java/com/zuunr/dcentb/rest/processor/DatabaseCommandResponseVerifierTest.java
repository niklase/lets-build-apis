package com.zuunr.dcentb.rest.processor;

import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Covers every outcome DatabaseCommandResponseVerifier can produce from a mongoResult:
 * ok!=1 -> 500, a duplicate-key writeError (11000, the optimistic-concurrency conflict
 * PATCH/PUT/POST's etag-conditioned upsert produces when it loses the race) -> 409, any
 * other writeError -> 500, a delete matching zero documents (the same conflict, but for
 * DELETE's etag-conditioned query, which fails closed rather than raising a writeError) ->
 * 409, and that success (a delete that matched one document, or any command with no
 * mongoCommand at all - e.g. a plain read) leaves the requestContext untouched.
 */
class DatabaseCommandResponseVerifierTest {

    private final DatabaseCommandResponseVerifier processor =
            new DatabaseCommandResponseVerifier(JsonObject.EMPTY.jsonValue());

    @Test
    void notOk_returns500() {
        JsonObject requestContext = JsonObject.EMPTY
                .put("mongoCommand", JsonObject.EMPTY.put("update", JsonObject.EMPTY))
                .put("mongoResult", JsonObject.EMPTY.put("ok", 0));

        JsonObject result = processor.process(requestContext);

        assertEquals(500, result.get("response").getJsonObject().get("status").getInteger());
    }

    @Test
    void duplicateKeyWriteError_returns409() {
        JsonObject requestContext = JsonObject.EMPTY
                .put("mongoCommand", JsonObject.EMPTY.put("update", JsonObject.EMPTY))
                .put("mongoResult", JsonObject.EMPTY
                        .put("ok", 1)
                        .put("writeErrors", com.zuunr.json.JsonArray.of(
                                JsonObject.EMPTY.put("code", 11000))));

        JsonObject result = processor.process(requestContext);

        assertEquals(409, result.get("response").getJsonObject().get("status").getInteger());
    }

    @Test
    void otherWriteError_returns500() {
        JsonObject requestContext = JsonObject.EMPTY
                .put("mongoCommand", JsonObject.EMPTY.put("update", JsonObject.EMPTY))
                .put("mongoResult", JsonObject.EMPTY
                        .put("ok", 1)
                        .put("writeErrors", com.zuunr.json.JsonArray.of(
                                JsonObject.EMPTY.put("code", 12345))));

        JsonObject result = processor.process(requestContext);

        assertEquals(500, result.get("response").getJsonObject().get("status").getInteger());
    }

    @Test
    void deleteMatchingZeroDocuments_returns409() {
        JsonObject requestContext = JsonObject.EMPTY
                .put("mongoCommand", JsonObject.EMPTY.put("delete", JsonObject.EMPTY))
                .put("mongoResult", JsonObject.EMPTY.put("ok", 1).put("n", 0));

        JsonObject result = processor.process(requestContext);

        assertEquals(409, result.get("response").getJsonObject().get("status").getInteger());
    }

    @Test
    void deleteMatchingOneDocument_isUntouched() {
        JsonObject requestContext = JsonObject.EMPTY
                .put("mongoCommand", JsonObject.EMPTY.put("delete", JsonObject.EMPTY))
                .put("mongoResult", JsonObject.EMPTY.put("ok", 1).put("n", 1));

        JsonObject result = processor.process(requestContext);

        assertFalse(result.containsKey("response"));
    }

    @Test
    void nonDeleteCommandWithoutN_isUntouched() {
        JsonObject requestContext = JsonObject.EMPTY
                .put("mongoCommand", JsonObject.EMPTY.put("find", JsonObject.EMPTY))
                .put("mongoResult", JsonObject.EMPTY.put("ok", 1));

        JsonObject result = processor.process(requestContext);

        assertFalse(result.containsKey("response"));
    }

    @Test
    void noMongoCommand_isUntouched() {
        JsonObject requestContext = JsonObject.EMPTY.put("someOtherKey", "value");

        JsonObject result = processor.process(requestContext);

        assertEquals(requestContext, result);
        assertNull(result.get("response", (JsonValue) null));
    }
}
