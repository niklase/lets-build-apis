package com.zuunr.dcentb.rest.html;

import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;

import java.util.ArrayList;
import java.util.List;

/**
 * The one recursive "render a value" function used everywhere a value is rendered - the
 * detail view, and each column of the list view - see html-ui-rendering-design.md section
 * 9. A resolved schema (completed by EffectiveXUi, with its own "x-ui.properties" already
 * in place) drives order and labels when one is available; without one (an error body, or
 * any schema-less fallback) it degrades gracefully to the value's own natural key order
 * and raw keys as labels - still correct, just undecorated.
 */
public final class ValueRenderer {

    private ValueRenderer() {
    }

    public static String renderValue(JsonValue value, JsonValue schema, String propertyName,
                                      JsonObject componentSchemas, JsonObject requestHeaders) {
        if (value == null || value.isNull()) {
            return "<span class=\"empty\">&mdash;</span>";
        }
        if (value.isJsonObject()) {
            return renderObject(value.getJsonObject(), SchemaRef.resolve(schema, componentSchemas), componentSchemas, requestHeaders);
        }
        if (value.isJsonArray()) {
            return renderArray(value.getJsonArray(), SchemaRef.resolve(schema, componentSchemas), componentSchemas, requestHeaders);
        }
        return renderScalar(value, propertyName);
    }

    private static String renderObject(JsonObject object, JsonObject resolvedSchema, JsonObject componentSchemas, JsonObject requestHeaders) {
        JsonArray xUiProperties = resolvedSchema == null ? null
                : resolvedSchema.get(JsonArray.of("x-ui", "properties"), JsonValue.NULL).getJsonArray();

        List<String> names = new ArrayList<>();
        if (xUiProperties != null) {
            for (JsonValue entry : xUiProperties) {
                names.add(entry.get("name").getString());
            }
        } else {
            names.addAll(object.keySet());
        }

        StringBuilder html = new StringBuilder("<dl>");
        for (String name : names) {
            JsonValue childValue = object.get(name);
            if (childValue == null) {
                continue; // absent from this (possibly role-filtered) body - not an error, see design doc section 3
            }
            String label = DisplayNameResolver.resolve(requestHeaders, displayNameOf(name, xUiProperties), name);
            JsonValue childSchema = resolvedSchema == null ? null
                    : resolvedSchema.get(JsonArray.of("properties", name));
            html.append("<dt>").append(HtmlEscaper.escape(label)).append("</dt><dd>")
                    .append(renderValue(childValue, childSchema, name, componentSchemas, requestHeaders))
                    .append("</dd>");
        }
        html.append("</dl>");
        return html.toString();
    }

    private static JsonValue displayNameOf(String propertyName, JsonArray xUiProperties) {
        if (xUiProperties == null) {
            return null;
        }
        for (JsonValue entry : xUiProperties) {
            if (propertyName.equals(entry.get("name").getString())) {
                return entry.get("displayName");
            }
        }
        return null;
    }

    private static String renderArray(JsonArray array, JsonObject resolvedArraySchema, JsonObject componentSchemas, JsonObject requestHeaders) {
        if (array.isEmpty()) {
            return "<span class=\"empty\">(none)</span>";
        }
        JsonValue itemsSchema = resolvedArraySchema == null ? null : resolvedArraySchema.get("items");
        StringBuilder html = new StringBuilder("<ul>");
        for (JsonValue element : array) {
            html.append("<li>").append(renderValue(element, itemsSchema, null, componentSchemas, requestHeaders)).append("</li>");
        }
        html.append("</ul>");
        return html.toString();
    }

    private static String renderScalar(JsonValue value, String propertyName) {
        if (value.isString()) {
            String text = value.getString();
            if ("href".equals(propertyName)) {
                String escaped = HtmlEscaper.escape(text);
                return "<a href=\"" + escaped + "\">" + escaped + "</a>";
            }
            return HtmlEscaper.escape(text);
        }
        if (value.isBoolean()) {
            return value.getBoolean() ? "true" : "false";
        }
        return HtmlEscaper.escape(String.valueOf(value.getValue()));
    }
}
