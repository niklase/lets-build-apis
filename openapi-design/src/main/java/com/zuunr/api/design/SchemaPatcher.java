package com.zuunr.api.design;

import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonObjectBuilder;
import com.zuunr.json.JsonValue;
import com.zuunr.json.pointer.JsonPointer;
import com.zuunr.json.schema.JsonSchema;
import com.zuunr.json.schema.Keywords;

public class SchemaPatcher {

    public static JsonSchema patch(JsonSchema original, JsonSchema patch) {
        return patch(original, patch, 5);
    }

    public static JsonSchema patch(JsonSchema original, JsonSchema patch, int maxPathRefs) {
        JsonObject orgSchema = original.asJsonValue().getJsonObject();
        JsonObject patchSchema = patch.asJsonValue().getJsonObject();

        JsonObject patched = patch(orgSchema, patchSchema, maxPathRefs, orgSchema, patchSchema);


        JsonValue orgSchemaDefs = orgSchema.get(Keywords.DEFS);

        if (patched == null) {
            return null;
        }
        return orgSchemaDefs == null
                ? patched.remove(Keywords.DEFS).as(JsonSchema.class)
                : patched.put(Keywords.DEFS, orgSchemaDefs).as(JsonSchema.class);
    }

    private static JsonObject patch(JsonObject original, JsonObject patch, int maxPatchRefs, JsonObject jsonDocOriginal, JsonObject jsonDocPatch) {

        try {
            JsonObject currentSchema;
            ReferencedSchema originalRefSchema = getRefSchema(original, jsonDocOriginal, -1);
            currentSchema = originalRefSchema.schema == null ? original : originalRefSchema.schema;
            ReferencedSchema patchRefSchema = getRefSchema(patch, jsonDocPatch, maxPatchRefs);

            if (patchRefSchema.schema != null) {
                patch = patchRefSchema.schema;
                maxPatchRefs = maxPatchRefs - patchRefSchema.depth;

            }

            JsonObject result = currentSchema.putAll(patch);
            JsonObject currentProperties = currentSchema.get(Keywords.PROPERTIES, JsonValue.NULL).getJsonObject();

            if (currentProperties != null) {
                JsonObject patchProperties = patch.get(Keywords.PROPERTIES, JsonValue.NULL).getJsonObject();
                JsonObject patchedProperties = patchProperties(currentProperties, patchProperties, maxPatchRefs, jsonDocOriginal, jsonDocPatch);
                result = result.put(Keywords.PROPERTIES, patchedProperties);
                result = result.put(Keywords.ADDITIONAL_PROPERTIES, false);
            }

            JsonObject currentItems = currentSchema.get(Keywords.ITEMS, JsonValue.NULL).getJsonObject();
            if (currentItems != null) {
                JsonObject patchItems = patch.get(Keywords.ITEMS, JsonValue.NULL).getJsonObject();
                result = result.put(Keywords.ITEMS, patch(currentItems, patchItems, maxPatchRefs, jsonDocOriginal, jsonDocPatch));
            }

            return result;
        } catch (RefDepthException e) {
            return null;
        }
    }

    private static ReferencedSchema getRefSchema(JsonObject schema, JsonObject jsonDoc, int maxRefDepth) throws RefDepthException {
        JsonValue ref = schema.get(Keywords.REF);
        if (ref == null) {
            return new ReferencedSchema(0, null);
        } else if (maxRefDepth == 0) {
            throw new RefDepthException();
        }

        JsonObject refValue = jsonDoc.get(ref.as(JsonPointer.class), JsonValue.NULL).getJsonObject();

        ReferencedSchema nested = getRefSchema(refValue, jsonDoc, maxRefDepth - 1);
        return nested.schema == null
                ? new ReferencedSchema(nested.depth + 1, refValue)
                : nested;
    }

    private static JsonObject patchProperties(JsonObject original, JsonObject patch, int maxPatchRefs, JsonObject jsonDocOriginal, JsonObject jsonDocPatch) {

        JsonObjectBuilder builder = original.builder();
        JsonArray keys = original.keys();
        JsonArray values = original.values();

        for (int i = 0; i < original.size(); i++) {
            String key = keys.get(i).getString();
            JsonObject value = values.get(i).getJsonObject();
            JsonObject patchedSchema = patch(value, patch.get(key).getJsonObject(), maxPatchRefs, jsonDocOriginal, jsonDocPatch);
            if (patchedSchema == null) {
                builder.remove(key);
            } else {
                builder.put(key, patchedSchema);
            }
        }
        return builder.build();
    }

    private static final class RefDepthException extends Exception {
    }

    private record ReferencedSchema(int depth, JsonObject schema) {
        private ReferencedSchema(int depth, JsonObject schema) {
            this.depth = depth < 0 ? -1 : depth;
            this.schema = schema;
        }

        @Override
        public String toString() {
            return JsonObject.EMPTY
                    .put("depth", depth)
                    .put("schema", schema == null ? JsonValue.NULL : schema.jsonValue())
                    .toString();
        }
    }
}