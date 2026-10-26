package com.zuunr.dcentb.rest.html;

import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import com.zuunr.json.JsonValueFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValueRendererTest {

    private static final JsonObject EN_US = JsonObject.EMPTY.put("accept-language", JsonArray.of("en-US"));

    @Test
    @DisplayName("an object with a resolved x-ui renders in that order, with labels")
    void objectRendersInXUiOrderWithLabels() {
        JsonValue value = JsonValueFactory.create("""
                {"title": "Buy oat milk", "done": false}
                """);
        JsonValue schema = JsonValueFactory.create("""
                {"x-ui": {"properties": [
                  {"name": "title", "displayName": {"en_US": "Title"}},
                  {"name": "done", "displayName": {"en_US": "Done"}}
                ]}}
                """);

        String html = ValueRenderer.renderValue(value, schema, null, JsonObject.EMPTY, EN_US);

        assertEquals("<dl><dt>Title</dt><dd>Buy oat milk</dd><dt>Done</dt><dd>false</dd></dl>", html);
    }

    @Test
    @DisplayName("a property named \"href\" renders as a clickable link")
    void hrefPropertyRendersAsLink() {
        JsonValue value = JsonValueFactory.create("""
                {"href": "/todos/123"}
                """);

        String html = ValueRenderer.renderValue(value, null, null, JsonObject.EMPTY, JsonObject.EMPTY);

        assertEquals("<dl><dt>href</dt><dd><a href=\"/todos/123\">/todos/123</a></dd></dl>", html);
    }

    @Test
    @DisplayName("without a resolved schema, falls back to the value's own key order and raw keys as labels")
    void schemaLessFallsBackToRawKeys() {
        JsonValue value = JsonValueFactory.create("""
                {"errors": ["title is required"]}
                """);

        String html = ValueRenderer.renderValue(value, null, null, JsonObject.EMPTY, JsonObject.EMPTY);

        assertEquals("<dl><dt>errors</dt><dd><ul><li>title is required</li></ul></dd></dl>", html);
    }

    @Test
    @DisplayName("a nested object gets its own x-ui-driven rendering, independent of the parent")
    void nestedObjectUsesItsOwnXUi() {
        JsonValue value = JsonValueFactory.create("""
                {"classSummary": {"studentCount": 24, "atRiskCount": 3}}
                """);
        JsonValue schema = JsonValueFactory.create("""
                {
                  "x-ui": {"properties": [{"name": "classSummary", "displayName": {"en_US": "Class summary"}}]},
                  "properties": {
                    "classSummary": {
                      "x-ui": {"properties": [
                        {"name": "studentCount", "displayName": {"en_US": "Student count"}},
                        {"name": "atRiskCount", "displayName": {"en_US": "At risk"}}
                      ]}
                    }
                  }
                }
                """);

        String html = ValueRenderer.renderValue(value, schema, null, JsonObject.EMPTY, EN_US);

        assertEquals("<dl><dt>Class summary</dt><dd><dl><dt>Student count</dt><dd>24</dd>"
                + "<dt>At risk</dt><dd>3</dd></dl></dd></dl>", html);
    }

    @Test
    @DisplayName("an empty array renders a placeholder, not an empty list")
    void emptyArrayRendersPlaceholder() {
        JsonValue value = JsonValueFactory.create("""
                {"tags": []}
                """);

        String html = ValueRenderer.renderValue(value, null, null, JsonObject.EMPTY, JsonObject.EMPTY);

        assertTrue(html.contains("(none)"));
    }

    @Test
    @DisplayName("null renders as a placeholder dash, not the literal word null")
    void nullRendersAsPlaceholder() {
        String html = ValueRenderer.renderValue(JsonValue.NULL, null, null, JsonObject.EMPTY, JsonObject.EMPTY);

        assertTrue(html.contains("mdash"));
    }

    @Test
    @DisplayName("field values are HTML-escaped")
    void valuesAreEscaped() {
        JsonValue value = JsonValueFactory.create("""
                {"title": "<script>alert(1)</script>"}
                """);

        String html = ValueRenderer.renderValue(value, null, null, JsonObject.EMPTY, JsonObject.EMPTY);

        assertTrue(html.contains("&lt;script&gt;"));
        assertTrue(!html.contains("<script>"));
    }

    @Test
    @DisplayName("a $ref'd schema is resolved against componentSchemas before reading its x-ui")
    void refSchemaIsResolved() {
        JsonValue value = JsonValueFactory.create("""
                {"id": "abc", "createdAt": "2026-01-01"}
                """);
        JsonValue schema = JsonValueFactory.create("""
                {"$ref": "#/components/schemas/Meta"}
                """);
        JsonObject componentSchemas = JsonValueFactory.create("""
                {"Meta": {"x-ui": {"properties": [
                  {"name": "createdAt", "displayName": {"en_US": "Created"}},
                  {"name": "id", "displayName": {"en_US": "Id"}}
                ]}}}
                """).getJsonObject();

        String html = ValueRenderer.renderValue(value, schema, null, componentSchemas, EN_US);

        assertEquals("<dl><dt>Created</dt><dd>2026-01-01</dd><dt>Id</dt><dd>abc</dd></dl>", html);
    }
}
