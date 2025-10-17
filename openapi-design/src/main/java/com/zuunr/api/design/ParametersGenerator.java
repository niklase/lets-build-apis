package com.zuunr.api.design;

import com.zuunr.json.*;
import com.zuunr.json.schema.Keywords;

public class ParametersGenerator {

    public static JsonArray createCollectionQueryParameters(JsonObject dbModelSchema) {

        JsonArrayBuilder deser = JsonArray.EMPTY.builder();
        JsonArrayBuilder orderByEnum = JsonArray.EMPTY.builder();
        JsonArray paths = dbModelSchema.jsonValue().getPaths(true);

        for (int i = 0; i < paths.size(); i++) {
            JsonArray path = paths.get(i).getJsonArray();

            String fieldName = createFieldNameFromTypePath(path);
            if (fieldName != null) {
                JsonValue type = path.last();
                orderByEnum.add(fieldName);
                deser.addAll(createFilterParameters(fieldName, type));
            }
        }
        deser.addAll(createCommonParameters());
        deser.add(JsonObject.EMPTY.builder()
                .put("name", "orderBy")
                .put("in", "query")
                .put("required", false)
                .put("explode", false)
                .put("schema", JsonObject.EMPTY
                        .put("type", Keywords.ARRAY)
                        .put("items", JsonObject.EMPTY
                                        .put("type", Keywords.STRING)
                                        .put("pattern", createOrderByPattern(orderByEnum.build()))
                                //.put("enum", orderByEnum.build())
                        )).build());
        return deser.build();
    }

    private static String createOrderByPattern(JsonArray fields) {
        StringBuilder builder = null;

        for (JsonValue field : fields) {
            builder = builder == null ? new StringBuilder("^(") : builder.append("|");
            builder.append(field.getString().replace(".", "[.]")).append("( asc| desc)?");
        }
        return builder == null ? null : builder.append(")$").toString();
    }

    /**
     * @param path must start with keyword "properties" or "items" and the second last item must be "type"
     * @return the dot separated path to the value inside an instance of object described by the path
     */
    static String createFieldNameFromTypePath(JsonArray path) {
        StringBuilder builder = null;
        boolean schemaKeyword = true;

        // Now make sure it is a type-path for a leaf JsonValue
        // must end with string type and must not be an array or an object (then it is not a leaf)

        JsonValue lastInPath = path.last();
        if (!lastInPath.isString()) {
            return null;
        } else {
            String lastAsString = lastInPath.getString();
            if (Keywords.ARRAY.equals(lastAsString) || Keywords.OBJECT.equals(lastAsString)) {
                return null;
            }
        }

        for (int i = 0; i < path.size(); i++) {
            JsonValue element = path.get(i);
            if (!schemaKeyword) {
                builder = builder == null ? new StringBuilder() : builder.append('.');
                builder.append(element.getString());
                schemaKeyword = true;
            } else if (element.isString()) {
                String elemString = element.getString();
                if (Keywords.PROPERTIES.equals(elemString)) {
                    schemaKeyword = false;
                } else if (Keywords.TYPE.equals(elemString)) {
                    return builder == null ? null : builder.toString();
                }
                // the "items" case will keep schemaKeyword as true
            } else {
                return null;
            }
        }
        return null;
    }

    static JsonArray createFilterParameters(String fieldName, JsonValue type) {

        JsonArrayBuilder builder = JsonArray.EMPTY.builder();
        String filterAndName = "filter." + fieldName;

        builder
                .add(JsonObject.EMPTY
                        .put("name", filterAndName + ".eq")
                        .put("in", "query")
                        .put("required", false)
                        .put("schema", JsonObject.EMPTY
                                .put("type", type)))
                .add(JsonObject.EMPTY.builder()
                        .put("name", filterAndName + ".gt")
                        .put("in", "query")
                        .put("required", false)
                        .put("schema", JsonObject.EMPTY
                                .put("type", type)).build())
                .add(JsonObject.EMPTY.builder()
                        .put("name", filterAndName + ".gte")
                        .put("in", "query")
                        .put("required", false)
                        .put("schema", JsonObject.EMPTY
                                .put("type", type)).build())
                .add(JsonObject.EMPTY.builder()
                        .put("name", filterAndName + ".lt")
                        .put("in", "query")
                        .put("required", false)
                        .put("schema", JsonObject.EMPTY
                                .put("type", type)).build())
                .add(JsonObject.EMPTY.builder()
                        .put("name", filterAndName + ".lte")
                        .put("in", "query")
                        .put("required", false)
                        .put("schema", JsonObject.EMPTY
                                .put("type", type)).build())
                .add(JsonObject.EMPTY.builder()
                        .put("name", filterAndName + ".ne")
                        .put("in", "query")
                        .put("required", false)
                        .put("schema", JsonObject.EMPTY
                                .put("type", type)).build());

        if (Keywords.STRING.equals(type.getString())) {
            builder
                    .add(JsonObject.EMPTY.builder()
                            .put("name", filterAndName + ".regex")
                            .put("in", "query")
                            .put("required", false)
                            .put("schema", JsonObject.EMPTY
                                    .put("type", type)).build());
        }
        return builder.build();
    }

    public static JsonArray createCommonParameters() {
        JsonArrayBuilder builder = JsonArray.EMPTY.builder();
        builder
                .add(JsonObject.EMPTY.builder()
                        .put("name", "offset")
                        .put("in", "query")
                        .put("required", false)
                        .put("schema", JsonObject.EMPTY
                                .put("type", "integer")).build())
                .add(JsonObject.EMPTY.builder()
                        .put("name", "limit")
                        .put("in", "query")
                        .put("required", false)
                        .put("schema", JsonObject.EMPTY
                                .put("type", "integer")).build());
        return builder.build();
    }

    private static final JsonValue PATH_PARAMETER_ID = JsonValueFactory.create("""
            {
                "schema": {
                    "type": "string"
                },
                "name": "id",
                "required": true,
                "in": "path"
            }
            """);

    public static JsonArray addPathIdToParameters(JsonArray parameters) {
        return parameters.addFirst(PATH_PARAMETER_ID);
    }
}