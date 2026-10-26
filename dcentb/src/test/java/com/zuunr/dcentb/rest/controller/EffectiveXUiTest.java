package com.zuunr.dcentb.rest.controller;

import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonValue;
import com.zuunr.json.JsonValueFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Covers EffectiveXUi's completion of "x-ui.properties" on 2xx response schemas - see
 * html-ui-rendering-design.md sections 3 and 4.
 */
class EffectiveXUiTest {

    @Test
    @DisplayName("declared entries are kept in order, remaining properties are appended alphabetically")
    void declaredEntriesFirstThenAlphabeticalRemainder() {
        JsonValue doc = JsonValueFactory.create("""
                {
                  "paths": {"/todos": {"get": {"responses": {"200": {"content": {"application/json": {"schema": {
                    "type": "object",
                    "x-ui": {"properties": [{"name": "title", "displayName": {"en_US": "Title"}}]},
                    "properties": {
                      "title": {"type": "string"},
                      "done": {"type": "boolean"},
                      "assignee": {"type": "string"}
                    }
                  }}}}}}}},
                  "components": {"schemas": {}}
                }
                """);

        JsonValue effective = doc.as(EffectiveXUi.class).getJsonValue();
        JsonArray xUiProperties = effective.get(JsonArray.of(
                "paths", "/todos", "get", "responses", "200", "content", "application/json", "schema", "x-ui", "properties")).getJsonArray();

        assertEquals(3, xUiProperties.size());
        assertEquals("title", xUiProperties.get(0).get("name").getString());
        assertEquals("Title", xUiProperties.get(0).get(JsonArray.of("displayName", "en_US")).getString());
        assertEquals("assignee", xUiProperties.get(1).get("name").getString());
        assertEquals("done", xUiProperties.get(2).get("name").getString());
    }

    @Test
    @DisplayName("a $ref'd schema is completed once and shared by every usage site")
    void sharedRefIsCompletedOnce() {
        JsonValue doc = JsonValueFactory.create("""
                {
                  "paths": {
                    "/teachers": {"get": {"responses": {"200": {"content": {"application/json": {"schema": {
                      "type": "object", "properties": {"meta": {"$ref": "#/components/schemas/Meta"}}
                    }}}}}}},
                    "/students": {"get": {"responses": {"200": {"content": {"application/json": {"schema": {
                      "type": "object", "properties": {"meta": {"$ref": "#/components/schemas/Meta"}}
                    }}}}}}}
                  },
                  "components": {"schemas": {"Meta": {"type": "object", "properties": {"id": {"type": "string"}, "createdAt": {"type": "string"}}}}}
                }
                """);

        JsonValue effective = doc.as(EffectiveXUi.class).getJsonValue();
        JsonArray metaXUiProperties = effective.get(JsonArray.of("components", "schemas", "Meta", "x-ui", "properties")).getJsonArray();

        assertEquals(2, metaXUiProperties.size());
        assertEquals("createdAt", metaXUiProperties.get(0).get("name").getString());
        assertEquals("id", metaXUiProperties.get(1).get("name").getString());
    }

    @Test
    @DisplayName("a nested inline object gets its own, independent x-ui.properties")
    void nestedInlineObjectGetsItsOwnXUi() {
        JsonValue doc = JsonValueFactory.create("""
                {
                  "paths": {"/teachers": {"get": {"responses": {"200": {"content": {"application/json": {"schema": {
                    "type": "object",
                    "x-ui": {"properties": [{"name": "classSummary"}]},
                    "properties": {
                      "classSummary": {
                        "type": "object",
                        "properties": {"studentCount": {"type": "integer"}, "atRiskCount": {"type": "integer"}}
                      },
                      "name": {"type": "string"}
                    }
                  }}}}}}}},
                  "components": {"schemas": {}}
                }
                """);

        JsonValue effective = doc.as(EffectiveXUi.class).getJsonValue();
        JsonArray nestedXUiProperties = effective.get(JsonArray.of(
                "paths", "/teachers", "get", "responses", "200", "content", "application/json", "schema",
                "properties", "classSummary", "x-ui", "properties")).getJsonArray();

        assertEquals(2, nestedXUiProperties.size());
        assertEquals("atRiskCount", nestedXUiProperties.get(0).get("name").getString());
        assertEquals("studentCount", nestedXUiProperties.get(1).get("name").getString());
    }

    @Test
    @DisplayName("an array property's object items are completed the same way, reached through \"items\"")
    void arrayItemsAreCompletedThroughItems() {
        JsonValue doc = JsonValueFactory.create("""
                {
                  "paths": {"/teachers": {"get": {"responses": {"200": {"content": {"application/json": {"schema": {
                    "type": "object",
                    "properties": {
                      "grades": {
                        "type": "array",
                        "items": {"type": "object", "properties": {"subject": {"type": "string"}, "score": {"type": "integer"}}}
                      }
                    }
                  }}}}}}}},
                  "components": {"schemas": {}}
                }
                """);

        JsonValue effective = doc.as(EffectiveXUi.class).getJsonValue();
        JsonArray itemXUiProperties = effective.get(JsonArray.of(
                "paths", "/teachers", "get", "responses", "200", "content", "application/json", "schema",
                "properties", "grades", "items", "x-ui", "properties")).getJsonArray();

        assertEquals(2, itemXUiProperties.size());
        assertEquals("score", itemXUiProperties.get(0).get("name").getString());
        assertEquals("subject", itemXUiProperties.get(1).get("name").getString());
    }

    @Test
    @DisplayName("a self-referential schema does not recurse forever")
    void selfReferentialSchemaDoesNotLoopForever() {
        JsonValue doc = JsonValueFactory.create("""
                {
                  "paths": {"/nodes": {"get": {"responses": {"200": {"content": {"application/json": {"schema": {
                    "$ref": "#/components/schemas/Node"
                  }}}}}}}},
                  "components": {"schemas": {"Node": {
                    "type": "object",
                    "properties": {"name": {"type": "string"}, "child": {"$ref": "#/components/schemas/Node"}}
                  }}}
                }
                """);

        JsonValue effective = doc.as(EffectiveXUi.class).getJsonValue();
        JsonArray nodeXUiProperties = effective.get(JsonArray.of("components", "schemas", "Node", "x-ui", "properties")).getJsonArray();

        assertEquals(2, nodeXUiProperties.size());
        assertEquals("child", nodeXUiProperties.get(0).get("name").getString());
        assertEquals("name", nodeXUiProperties.get(1).get("name").getString());
    }

    @Test
    @DisplayName("a request body schema (not a 2xx response) is left untouched")
    void requestBodySchemaIsUntouched() {
        JsonValue doc = JsonValueFactory.create("""
                {
                  "paths": {"/todos": {"post": {
                    "requestBody": {"content": {"application/json": {"schema": {
                      "type": "object", "properties": {"title": {"type": "string"}}
                    }}}},
                    "responses": {"201": {"content": {"application/json": {"schema": {
                      "type": "object", "properties": {"title": {"type": "string"}}
                    }}}}}
                  }}},
                  "components": {"schemas": {}}
                }
                """);

        JsonValue effective = doc.as(EffectiveXUi.class).getJsonValue();

        JsonValue requestBodyXUi = effective.get(JsonArray.of(
                "paths", "/todos", "post", "requestBody", "content", "application/json", "schema", "x-ui"));
        assertEquals(null, requestBodyXUi);

        JsonArray responseXUiProperties = effective.get(JsonArray.of(
                "paths", "/todos", "post", "responses", "201", "content", "application/json", "schema", "x-ui", "properties")).getJsonArray();
        assertEquals(1, responseXUiProperties.size());
    }

    @Test
    @DisplayName("an x-ui.properties entry naming a property the schema doesn't have fails fast")
    void unknownPropertyNameFailsFast() {
        JsonValue doc = JsonValueFactory.create("""
                {
                  "paths": {"/todos": {"get": {"responses": {"200": {"content": {"application/json": {"schema": {
                    "type": "object",
                    "x-ui": {"properties": [{"name": "titel"}]},
                    "properties": {"title": {"type": "string"}}
                  }}}}}}}},
                  "components": {"schemas": {}}
                }
                """);

        // Constructed directly rather than via doc.as(EffectiveXUi.class): as() wraps whatever
        // the constructor throws inside a reflection InvocationTargetException inside a
        // RuntimeException, which would hide the IllegalStateException's real type here - same
        // reasoning as EffectiveOpenApiDocTest's equivalent fail-fast tests.
        assertThrows(IllegalStateException.class, () -> new EffectiveXUi(doc));
    }
}
