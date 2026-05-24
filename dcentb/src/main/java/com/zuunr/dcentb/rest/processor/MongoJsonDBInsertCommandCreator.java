package com.zuunr.dcentb.rest.processor;

import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;

import java.util.UUID;

public class MongoJsonDBInsertCommandCreator implements Processor {

    private final JsonValue config;

    public MongoJsonDBInsertCommandCreator(JsonValue config) {
        this.config = config;
    }

    @Override
    public JsonObject process(JsonObject requestContext) {
        JsonObject deserializedRequest = requestContext.get(REQUEST).getJsonObject();
        JsonValue collection = config.get("operation").get(X_DCENTB).get("mongodb").get("collection");
        JsonObject body = deserializedRequest.get("body", JsonObject.EMPTY).getJsonObject();

        String id = UUID.randomUUID().toString().replace("-", "");
        JsonObject document = body.put("_id", id);

        JsonValue insertCommand = JsonObject.EMPTY.put("insert", JsonObject.EMPTY
                .put("collection", collection)
                .put("documents", JsonArray.of(document.jsonValue())))
                .jsonValue();

        return requestContext
                .put("mongoCommand", insertCommand);
    }
}
