package com.zuunr.mermaidschema.model;

import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import com.zuunr.json.pointer.JsonPointer;
import com.zuunr.json.schema.Keywords;

public class JsonSchemaFlattener {
    public static JsonObject flatten(JsonArray pathToSchema, JsonValue jsonSchema) {

        JsonObject result = JsonObject.EMPTY;

        result = result.put(pathToSchema.as(JsonPointer.class).getJsonPointerString().getString(), jsonSchema);
        // get all sub schemas in properties
        result = result.putAll(flattenProperties(pathToSchema, jsonSchema));
        result = result.putAll(flattenDefs(pathToSchema, jsonSchema));
        if (jsonSchema.get(Keywords.ITEMS, JsonValue.NULL).isJsonObject()) {
            result = result.putAll(flatten(pathToSchema.add(Keywords.ITEMS), jsonSchema.get(Keywords.ITEMS, JsonObject.EMPTY)));
        }
        return result;
    }

    private static JsonObject flattenDefs(JsonArray pathToSchema, JsonValue jsonSchema) {

        JsonObject result = JsonObject.EMPTY;
        JsonArray keys = jsonSchema.get(Keywords.DEFS, JsonObject.EMPTY).getJsonObject().keys();
        JsonArray values = jsonSchema.get(Keywords.DEFS, JsonObject.EMPTY).getJsonObject().values();

        for (int i = 0; i < keys.size(); i++) {
            result = result.putAll(flatten(pathToSchema.add(Keywords.DEFS).add(keys.get(i).getString()), values.get(i)));
        }
        return result;
    }

    private static JsonObject flattenProperties(JsonArray pathToSchema, JsonValue jsonSchema){
        JsonObject result = JsonObject.EMPTY;
        JsonObject properties = jsonSchema.get(Keywords.PROPERTIES, JsonValue.NULL).getJsonObject();
        if (properties == null) {
            return JsonObject.EMPTY;
        }


        JsonArray keys = properties.keys();
        JsonArray values = properties.values();

        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i).getString();
            JsonValue value = values.get(i);

            if (value.isJsonObject() && value.get(Keywords.PROPERTIES, JsonValue.NULL).isJsonObject()) {
                JsonArray path = pathToSchema.add(key);
                result = result.put(path.as(JsonPointer.class).getJsonPointerString().getString(), value);

                JsonObject nestedFlattened = flatten(path, value);
                result = result.putAll(nestedFlattened);
            }
        }
        return result;
    }
}
