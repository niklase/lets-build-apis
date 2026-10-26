package com.zuunr.dcentb.rest.html;

import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;

/** Resolves a "$ref" against "components.schemas" - render-time counterpart of EffectiveXUi's own (private) resolver. */
public final class SchemaRef {

    private static final String SCHEMAS_REF_PREFIX = "#/components/schemas/";

    private SchemaRef() {
    }

    public static JsonObject resolve(JsonValue schema, JsonObject componentSchemas) {
        if (schema == null) {
            return null;
        }
        JsonObject schemaObject = schema.getJsonObject();
        if (schemaObject == null) {
            return null;
        }
        JsonValue ref = schemaObject.get("$ref");
        if (ref == null || !ref.isString() || !ref.getString().startsWith(SCHEMAS_REF_PREFIX)) {
            return schemaObject;
        }
        String refName = ref.getString().substring(SCHEMAS_REF_PREFIX.length());
        return componentSchemas.get(refName, JsonValue.NULL).getJsonObject();
    }
}
