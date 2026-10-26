package com.zuunr.dcentb.rest.html;

import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;

import java.util.ArrayList;
import java.util.List;

/**
 * A collection response (a body shaped {"items": [...]}) gets a table, one row per item,
 * columns from the item schema's own resolved "x-ui.properties" - see
 * html-ui-rendering-design.md section 9's note on list-view columns. A row whose item has
 * "meta.href" links its first column to the item's own detail page.
 */
public final class CollectionTableRenderer {

    private CollectionTableRenderer() {
    }

    public static String renderTable(JsonArray items, JsonValue itemSchema, JsonObject componentSchemas, JsonObject requestHeaders) {
        if (items.isEmpty()) {
            return "<p class=\"empty\">(no items)</p>";
        }

        JsonObject resolvedItemSchema = SchemaRef.resolve(itemSchema, componentSchemas);
        JsonArray xUiProperties = resolvedItemSchema == null ? null
                : resolvedItemSchema.get(JsonArray.of("x-ui", "properties"), JsonValue.NULL).getJsonArray();

        List<String> columnNames = new ArrayList<>();
        if (xUiProperties != null) {
            for (JsonValue entry : xUiProperties) {
                columnNames.add(entry.get("name").getString());
            }
        } else {
            JsonObject first = items.get(0).getJsonObject();
            if (first != null) {
                columnNames.addAll(first.keySet());
            }
        }

        StringBuilder html = new StringBuilder("<table><thead><tr>");
        for (String name : columnNames) {
            String label = DisplayNameResolver.resolve(requestHeaders, ValueRenderer.displayNameOf(name, xUiProperties), name);
            html.append("<th>").append(HtmlEscaper.escape(label)).append("</th>");
        }
        html.append("</tr></thead><tbody>");

        for (JsonValue itemValue : items) {
            JsonObject item = itemValue.getJsonObject();
            String href = item == null ? null : item.get(JsonArray.of("meta", "href"), JsonValue.NULL).getString();
            html.append("<tr>");
            boolean firstColumn = true;
            for (String name : columnNames) {
                JsonValue cellValue = item == null ? null : item.get(name);
                JsonValue cellSchema = resolvedItemSchema == null ? null : resolvedItemSchema.get(JsonArray.of("properties", name));
                String cellHtml = cellValue == null ? "" : ValueRenderer.renderValue(cellValue, cellSchema, name, componentSchemas, requestHeaders);
                if (firstColumn && href != null && !href.isEmpty()) {
                    cellHtml = "<a href=\"" + HtmlEscaper.escape(href) + "\">" + cellHtml + "</a>";
                }
                html.append("<td>").append(cellHtml).append("</td>");
                firstColumn = false;
            }
            html.append("</tr>");
        }
        html.append("</tbody></table>");
        return html.toString();
    }
}
