package com.zuunr.dcentb.rest.processor.html;

import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import com.zuunr.json.JsonValueFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CollectionsIndexBuilderTest {

    private static final JsonValue CONFIG = JsonValueFactory.create("""
            {
              "x-dcentb": {"collections": {
                "todos": {"permissions": {"user": {}}, "ui": {"displayName": {"en_US": "Todos"}}},
                "admin-only": {"permissions": {"admin": {}}}
              }}
            }
            """);

    @Test
    @DisplayName("already having a response (e.g. 401 from AuthenticationProcessor) is left alone")
    void existingResponseIsLeftAlone() {
        JsonObject context = JsonObject.EMPTY.put("response", JsonObject.EMPTY.put("status", 401));

        JsonObject result = new CollectionsIndexBuilder(CONFIG).process(context);

        assertEquals(context, result);
    }

    @Test
    @DisplayName("a collection the caller's permission has no entry for is omitted")
    void inaccessibleCollectionIsOmitted() {
        JsonObject context = JsonObject.EMPTY.put("authenticatedUser", JsonObject.EMPTY.put("permissions", JsonArray.of("user")));

        JsonObject result = new CollectionsIndexBuilder(CONFIG).process(context);
        JsonArray collections = result.get("response").get("body").get("collections").getJsonArray();

        assertEquals(1, collections.size());
        assertEquals("todos", collections.get(0).get("name").getString());
    }

    @Test
    @DisplayName("SUPERUSER sees every collection regardless of its own permissions block")
    void superuserSeesEverything() {
        JsonObject context = JsonObject.EMPTY.put("authenticatedUser", JsonObject.EMPTY.put("permissions", JsonArray.of("SUPERUSER")));

        JsonObject result = new CollectionsIndexBuilder(CONFIG).process(context);
        JsonArray collections = result.get("response").get("body").get("collections").getJsonArray();

        assertEquals(2, collections.size());
    }

    @Test
    @DisplayName("no authenticatedUser at all (no security scheme configured) shows every collection")
    void noAuthenticatedUserShowsEverything() {
        JsonObject result = new CollectionsIndexBuilder(CONFIG).process(JsonObject.EMPTY);

        JsonArray collections = result.get("response").get("body").get("collections").getJsonArray();
        assertEquals(2, collections.size());
    }

    @Test
    @DisplayName("sets UNRESTRICTED_ACCESS so ResponseAccessController doesn't try to field-filter this body")
    void setsUnrestrictedAccess() {
        JsonObject result = new CollectionsIndexBuilder(CONFIG).process(JsonObject.EMPTY);

        assertTrue(result.get("unrestrictedAccess").getBoolean());
    }
}
