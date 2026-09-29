package com.zuunr.dcentb.rest.processor.apimodel;

import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the PUT/POST/PATCH/DELETE branches of NewStateCreator - in particular that the
 * consolidated withFreshMeta() helper (PUT and POST used to build this meta block with
 * duplicated inline code) still produces the right id/href per method, that PUT and POST
 * each mint a fresh, distinct etag, and that PATCH keeps the etag it started with (the
 * optimistic-concurrency precondition DatabaseCUDItemCommandCreator's update command relies
 * on) while only "updatedAt" moves.
 */
class NewStateCreatorTest {

    private static JsonValue configFor(String path, String method) {
        return JsonObject.EMPTY.put("path", path).put("method", method).jsonValue();
    }

    private static JsonObject requestContextFor(String uri, String pathId, JsonObject body) {
        JsonObject request = JsonObject.EMPTY
                .put("uri", uri);
        if (body != null) {
            request = request.put("body", body);
        }
        if (pathId != null) {
            request = request.put("pathParameters", JsonObject.EMPTY.put("id", pathId));
        }
        return JsonObject.EMPTY.put("request", request);
    }

    @Test
    void putCreatesFreshMetaFromPathId() {
        NewStateCreator processor = new NewStateCreator(configFor("/things/{id}", "PUT"));
        JsonObject requestContext = requestContextFor("/things/thing-1", "thing-1",
                JsonObject.EMPTY.put("name", "Widget"));

        JsonObject result = processor.process(requestContext);

        JsonObject meta = result.get("newState").getJsonObject().get("meta").getJsonObject();
        assertEquals("thing-1", meta.get("id").getString());
        assertEquals("/things/thing-1", meta.get("href").getString());
        assertEquals(meta.get("createdAt"), meta.get("updatedAt"));
        assertNotNull(meta.get("etag").getString());
        assertEquals("thing-1", result.get("itemId").getString());
    }

    @Test
    void postCreatesFreshMetaWithGeneratedIdAndHref() {
        NewStateCreator processor = new NewStateCreator(configFor("/things", "POST"));
        JsonObject requestContext = requestContextFor("/things", null,
                JsonObject.EMPTY.put("name", "Widget"));

        JsonObject result = processor.process(requestContext);

        JsonObject meta = result.get("newState").getJsonObject().get("meta").getJsonObject();
        String generatedId = meta.get("id").getString();
        assertNotNull(generatedId);
        assertEquals("/things/" + generatedId, meta.get("href").getString());
        assertEquals(meta.get("createdAt"), meta.get("updatedAt"));
        assertEquals(generatedId, result.get("itemId").getString());
    }

    @Test
    void putAndPostEachMintADistinctEtagPerCall() {
        NewStateCreator putProcessor = new NewStateCreator(configFor("/things/{id}", "PUT"));
        String putEtag1 = etagOf(putProcessor.process(requestContextFor("/things/thing-1", "thing-1",
                JsonObject.EMPTY.put("name", "Widget"))));
        String putEtag2 = etagOf(putProcessor.process(requestContextFor("/things/thing-2", "thing-2",
                JsonObject.EMPTY.put("name", "Widget"))));
        assertNotEquals(putEtag1, putEtag2);

        NewStateCreator postProcessor = new NewStateCreator(configFor("/things", "POST"));
        String postEtag1 = etagOf(postProcessor.process(requestContextFor("/things", null,
                JsonObject.EMPTY.put("name", "Widget"))));
        String postEtag2 = etagOf(postProcessor.process(requestContextFor("/things", null,
                JsonObject.EMPTY.put("name", "Widget"))));
        assertNotEquals(postEtag1, postEtag2);
    }

    @Test
    void patchMergesBodyOntoCurrentStateAndKeepsCurrentStatesEtag() {
        NewStateCreator processor = new NewStateCreator(configFor("/things/{id}", "PATCH"));
        JsonObject currentState = JsonObject.EMPTY
                .put("name", "Widget")
                .put("meta", JsonObject.EMPTY
                        .put("id", "thing-1")
                        .put("etag", "etag-v1")
                        .put("createdAt", "2020-01-01T00:00:00.000Z"));
        JsonObject requestContext = requestContextFor("/things/thing-1", "thing-1",
                JsonObject.EMPTY.put("name", "Widget 2"))
                .put("currentState", currentState);

        JsonObject result = processor.process(requestContext);

        JsonObject newState = result.get("newState").getJsonObject();
        assertEquals("Widget 2", newState.get("name").getString());
        JsonObject newStateMeta = newState.get("meta").getJsonObject();
        assertEquals("etag-v1", newStateMeta.get("etag").getString(),
                "PATCH must not mint a new etag - DatabaseCUDItemCommandCreator's optimistic-"
                        + "concurrency update query conditions on the etag this request read");
        assertEquals("2020-01-01T00:00:00.000Z", newStateMeta.get("createdAt").getString());
        assertNotNull(newStateMeta.get("updatedAt"));
        assertEquals("thing-1", result.get("itemId").getString());
    }

    @Test
    void deleteSetsNewStateNullAndKeepsPathItemId() {
        NewStateCreator processor = new NewStateCreator(configFor("/things/{id}", "DELETE"));
        JsonObject requestContext = requestContextFor("/things/thing-1", "thing-1", null);

        JsonObject result = processor.process(requestContext);

        assertTrue(result.get("newState").isNull());
        assertEquals("thing-1", result.get("itemId").getString());
    }

    private static String etagOf(JsonObject result) {
        return result.get("newState").getJsonObject().get("meta").getJsonObject().get("etag").getString();
    }
}
