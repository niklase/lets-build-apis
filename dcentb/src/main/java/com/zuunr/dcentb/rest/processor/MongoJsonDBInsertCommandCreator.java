package com.zuunr.dcentb.rest.processor;

import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;


public class MongoJsonDBInsertCommandCreator implements Processor {

    private final JsonValue config;

    public MongoJsonDBInsertCommandCreator(JsonValue config) {
        this.config = config;
    }

    @Override
    public JsonObject process(JsonObject requestContext) {
        JsonValue collection = config.get("operation").get(X_DCENTB).get("mongodb").get("collection");

        JsonValue mongoItem = requestContext.get("mongoItem");

        JsonValue upsertCommand = JsonObject.EMPTY.put("update", JsonObject.EMPTY
                .put("collection", collection)
                .put("updates", JsonArray.of(
                        JsonObject.EMPTY
                                .put("q", JsonObject.EMPTY
                                        .put("$and", JsonArray.of(
                                                JsonObject.EMPTY.put("_id", JsonArray.of(
                                                        JsonObject.EMPTY.put("$eq", mongoItem.get("_id")))),
                                                JsonObject.EMPTY.put("meta.etag", JsonArray.of(
                                                        JsonObject.EMPTY.put("$eq", mongoItem.get("meta", JsonObject.EMPTY).get("etag")))))))
                                .put("u", mongoItem)
                                .put("upsert", true))))
                .jsonValue();

        return requestContext
                .put("mongoCommand", upsertCommand);
    }
}
