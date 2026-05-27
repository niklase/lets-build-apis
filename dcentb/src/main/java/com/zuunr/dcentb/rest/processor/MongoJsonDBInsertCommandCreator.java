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
        JsonValue collection = config.get("operation").get(X_DCENTB).get("mongodb").get("collection");

        JsonValue mongoItem = requestContext.get("mongoItem");

        JsonValue insertCommand = JsonObject.EMPTY.put("insert", JsonObject.EMPTY
                .put("collection", collection)
                .put("documents", JsonArray.of(mongoItem)))
                .jsonValue();

        return requestContext
                .put("mongoCommand", insertCommand);
    }
}
