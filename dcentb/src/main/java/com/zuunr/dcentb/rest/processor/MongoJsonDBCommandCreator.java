package com.zuunr.dcentb.rest.processor;

import com.zuunr.json.*;
import com.zuunr.json.schema.validation.node.string.Pattern;
import com.zuunr.json.util.StringSplitter;

import java.util.regex.Matcher;

public class MongoJsonDBCommandCreator implements Processor {

    private static final JsonArray COLLECTION_NAME = JsonArray.of("operation", "x-dcentb", "mongodb", "collection");

    private JsonValue openApiConfig;
    private String collectionName;


    public MongoJsonDBCommandCreator(JsonValue openApiConfig) {

        this.openApiConfig = openApiConfig;
        this.collectionName = openApiConfig.get(COLLECTION_NAME).asString();
    }

    @Override
    public JsonObject process(JsonObject requestContext) {
        JsonObject deserializedRequest = requestContext.get(REQUEST).getJsonObject();
        JsonValue collection = requestContext.get("collection", collectionName);
        JsonArray filterOrder = requestContext.get("filterOrder", JsonArray.EMPTY).getJsonArray();
        JsonObject query = deserializedRequest.get("query", JsonObject.EMPTY).getJsonObject();
        JsonValue defaultLimit = requestContext.get("defaultLimit", 100);
        return requestContext.put("mongoCommand", createCommand(collection, query, filterOrder, defaultLimit));
    }

    public JsonValue createCommand(JsonValue collectionName, JsonObject query, JsonArray filterOrder, JsonValue defaultLimit) {
        JsonObjectBuilder find = JsonObject.EMPTY.builder();

        find.put("collection", collectionName);

        JsonValue skip = query.get("offset");
        if (skip != null && skip.getJsonArray().size() == 1) {
            skip = skip.getJsonArray().get(0);
            find.put("skip", skip);
        } else {
            find.put("skip", 0);
        }

        JsonValue limit = query.get("limit");
        if (limit == null || limit.getJsonArray().size() != 1) {
            limit = defaultLimit;
        } else {
            limit = limit.getJsonArray().get(0);
        }
        find.put("limit", limit);

        JsonArray filterArray = createFilterArray(query, filterOrder);
        if (!filterArray.isEmpty()) {
            find.put("filter", JsonObject.EMPTY.put("$and", filterArray));
        }

        JsonArray orderBy = query.get("orderBy", JsonValue.NULL).getJsonArray();
        if (orderBy != null) {
            JsonArray orderByAsObject = translateOrderBy(orderBy);
            find.put("sort", orderByAsObject);
        }
        return JsonObject.EMPTY.put("find", find.build()).jsonValue();
    }

    private JsonArray translateOrderBy(JsonArray fieldsToOrderBy) {
        JsonArrayBuilder builder = JsonArray.EMPTY.builder();
        for (JsonValue fieldToOrderBy : fieldsToOrderBy) {
            builder.add(translateOrderByItem(fieldToOrderBy));
        }
        return builder.build();
    }

    public JsonArray translateOrderByQueryString(JsonValue orderByJsonValue) {

        JsonArrayBuilder sort = JsonArray.EMPTY.builder();

        String orderBy = orderByJsonValue.getString();
        JsonArray array = StringSplitter.splitString(orderBy, ',');
        for (int i = 0; i < array.size(); i++) {
            sort.add(translateOrderByItem(array.get(i)));
        }
        return sort.build();
    }

    public JsonObject translateOrderByItem(JsonValue orderByJsonValue) {
        JsonArray tuple = StringSplitter.splitString(orderByJsonValue.getString(), ' ');
        String field = tuple.get(0).getString();
        return JsonObject.EMPTY.put(field,
                tuple.size() == 1
                        ? 1
                        : tuple.get(1).getString().equals("desc")
                          ? -1
                          : 1);
    }

    public JsonArray createFilterArray(JsonObject query, JsonArray orderOfTranslated) {
        JsonArrayBuilder builder = JsonArray.EMPTY.builder();

        JsonObjectBuilder leftOversBuilder = query.builder();
        for (int i = 0; i < orderOfTranslated.size(); i++) {
            String key = orderOfTranslated.get(i).getString();
            JsonArray values = query.get(key).getJsonArray();
            JsonValue value = values.get(0);
            if (value != null) {
                JsonValue translated = translate(key, value);
                if (translated != null) {
                    builder.add(translated);
                }
                leftOversBuilder.remove(key);
            }
        }

        JsonObject leftOvers = leftOversBuilder.build();
        JsonArray leftOverKeys = leftOvers.keys();
        JsonArray leftOverValues = leftOvers.values();

        for (int i = 0; i < leftOvers.size(); i++) {
            String key = leftOverKeys.get(i).getString();
            if (!key.startsWith("filter.")) {
                continue;
            }
            JsonArray keyArray = leftOverValues.get(i, JsonValue.NULL).getJsonArray();
            if (keyArray != null && keyArray.size() > 0) {
                JsonValue translated = translate(key, keyArray.get(0));
                if (translated != null) {
                    builder.add(translated);
                }
            }
        }
        return builder.build();
    }

    private JsonValue translate(String key, JsonValue value) {

        Pattern keyPattern = JsonValue.of("^filter[.](?<fieldName>.*)[.](?<constraint>[^.]+)$").as(Pattern.class); // TODO: Make this more performant. Regex not needed.

        Matcher matcher = keyPattern.compiled().matcher(key);

        boolean start = matcher.find();
        if (!start) {
            return null;
        } else {
            String fieldName = matcher.group("fieldName");
            String constraint = matcher.group("constraint");

            return JsonObject.EMPTY.put(fieldName, JsonArray.of(JsonObject.EMPTY.put("$" + constraint, value))).jsonValue();
        }
    }
}
