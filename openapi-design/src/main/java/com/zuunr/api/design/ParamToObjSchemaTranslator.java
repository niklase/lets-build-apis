package com.zuunr.api.design;

import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import com.zuunr.json.schema.JsonSchema;
import com.zuunr.json.schema.Keywords;

public class ParamToObjSchemaTranslator {
        private static final JsonObject BASE_SCHEMA = JsonObject.EMPTY.put(Keywords.TYPE, Keywords.OBJECT).put(Keywords.ADDITIONAL_PROPERTIES, false);
        private static final JsonObject BASE_SCHEMA_OF_FILTER = BASE_SCHEMA;
    public static final JsonSchema translate(JsonArray readCollectionParameters) {
        JsonObject schema = BASE_SCHEMA;
        for (JsonValue parameter : readCollectionParameters) {
            String paramName = parameter.get("name").getString();
            JsonValue parameterSchema = parameter.get("schema");
            boolean requiredParameter = parameter.get("required").getBoolean();
            String[] split = paramName.split("[.]");
            if ("filter".equals(split[0])) {
                if (schema.get(Keywords.PROPERTIES, JsonObject.EMPTY).get("filter") == null) {
                    schema = schema.put(JsonArray.of(Keywords.PROPERTIES, "filter"), BASE_SCHEMA_OF_FILTER);
                }

                String constraint = split[split.length - 1];
                String field = paramName.substring("filter.".length(), paramName.length() - constraint.length() - 1);
                schema = schema.put(JsonArray.of(Keywords.PROPERTIES, "filter", Keywords.PROPERTIES, field, Keywords.PROPERTIES, constraint), parameterSchema);
                schema = schema.put(JsonArray.of(Keywords.PROPERTIES, "filter", Keywords.PROPERTIES, field, Keywords.ADDITIONAL_PROPERTIES), false);
                if (requiredParameter) {
                    JsonArray required = schema.get(JsonArray.of(Keywords.PROPERTIES, "filter", Keywords.REQUIRED), JsonArray.EMPTY).getJsonArray();
                    if (!required.contains(field)) {
                        required = required.add(field);
                    }
                    schema = schema.put(JsonArray.of(Keywords.PROPERTIES, "filter", Keywords.REQUIRED), required);
                    schema = schema.put(JsonArray.of(Keywords.PROPERTIES, "filter", Keywords.PROPERTIES, field, Keywords.REQUIRED), JsonArray.of(constraint));
                }
            } else {
                if (requiredParameter) {
                    JsonArray required = schema.get(Keywords.REQUIRED, JsonArray.EMPTY).getJsonArray();
                    if (!required.contains(paramName)) {
                        required = required.add(paramName);
                        schema = schema.put(Keywords.REQUIRED, required);
                    }
                }
                schema = schema.put(JsonArray.of(Keywords.PROPERTIES, paramName), parameterSchema);
            }

        }
        return schema.as(JsonSchema.class);
    }
}
