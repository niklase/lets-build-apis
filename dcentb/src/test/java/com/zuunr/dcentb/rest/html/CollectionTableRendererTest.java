package com.zuunr.dcentb.rest.html;

import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import com.zuunr.json.JsonValueFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CollectionTableRendererTest {

    @Test
    @DisplayName("an empty collection renders a placeholder, not an empty table")
    void emptyCollectionRendersPlaceholder() {
        String html = CollectionTableRenderer.renderTable(JsonArray.EMPTY, null, JsonObject.EMPTY, JsonObject.EMPTY);

        assertEquals("<p class=\"empty\">(no items)</p>", html);
    }

    @Test
    @DisplayName("columns come from the item schema's x-ui.properties, in order, with labels")
    void columnsComeFromItemSchemaXUi() {
        JsonArray items = JsonValueFactory.create("""
                [{"title": "Buy oat milk", "done": false}, {"title": "Walk the dog", "done": true}]
                """).getJsonArray();
        JsonValue itemSchema = JsonValueFactory.create("""
                {"x-ui": {"properties": [
                  {"name": "title", "displayName": {"en_US": "Title"}},
                  {"name": "done", "displayName": {"en_US": "Done"}}
                ]}}
                """);
        JsonObject headers = JsonObject.EMPTY.put("accept-language", JsonArray.of("en-US"));

        String html = CollectionTableRenderer.renderTable(items, itemSchema, JsonObject.EMPTY, headers);

        assertEquals("<table><thead><tr><th>Title</th><th>Done</th></tr></thead><tbody>"
                + "<tr><td>Buy oat milk</td><td>false</td></tr>"
                + "<tr><td>Walk the dog</td><td>true</td></tr>"
                + "</tbody></table>", html);
    }

    @Test
    @DisplayName("an item with meta.href links its first column to the item's detail page")
    void firstColumnLinksToItemHref() {
        JsonArray items = JsonValueFactory.create("""
                [{"title": "Buy oat milk", "meta": {"href": "/todos/123"}}]
                """).getJsonArray();

        String html = CollectionTableRenderer.renderTable(items, null, JsonObject.EMPTY, JsonObject.EMPTY);

        assertTrue(html.contains("<td><a href=\"/todos/123\">Buy oat milk</a></td>"));
    }

    @Test
    @DisplayName("without a resolved item schema, columns fall back to the first item's own keys")
    void noSchemaFallsBackToFirstItemKeys() {
        JsonArray items = JsonValueFactory.create("""
                [{"title": "Buy oat milk"}]
                """).getJsonArray();

        String html = CollectionTableRenderer.renderTable(items, null, JsonObject.EMPTY, JsonObject.EMPTY);

        assertTrue(html.contains("<th>title</th>"));
    }
}
