package com.zuunr.api.openapi;

import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import com.zuunr.json.pointer.JsonPointer;
import com.zuunr.json.schema.Keywords;

import java.util.regex.Pattern;

public class AdditionalPropertiesApplicator {

    private static final Pattern PATTERN = Pattern.compile("^(([/]items)*(([/]properties)?([/][^/$]*))*)?[/]type$");

    private static final JsonValue OBJECT_TYPE = JsonValue.of(Keywords.OBJECT);
    private static final JsonValue TYPE = JsonValue.of(Keywords.TYPE);

    public static JsonObject updateAdditionalProperties(JsonObject schema, JsonValue additionalPropertiesSchema) {

        JsonObject updateSchema = schema;
        for (JsonValue arrayPath : schema.jsonValue().getPaths(false)) {


            JsonArray path = arrayPath.getJsonArray();

            if (TYPE.equals(path.last())) { // Optimization to avoid regex matching for all paths
                JsonPointer jsonPointer = path.as(JsonPointer.class);
                String jsonPointerString = jsonPointer.getJsonPointerString().getString();
                if (PATTERN.matcher(jsonPointerString).matches()) {
                    JsonValue type = schema.get(arrayPath.getJsonArray());
                    if (type.isJsonArray() && type.getJsonArray().contains(OBJECT_TYPE) || OBJECT_TYPE.equals(type)) {
                        updateSchema = updateSchema.put(path.allButLast().add(Keywords.ADDITIONAL_PROPERTIES), additionalPropertiesSchema);
                    }
                }
            }
        }
        return updateSchema;

    }
}
