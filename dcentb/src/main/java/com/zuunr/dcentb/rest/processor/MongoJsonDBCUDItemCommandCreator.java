package com.zuunr.dcentb.rest.processor;

import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;


public class MongoJsonDBCUDItemCommandCreator extends Processor {

    private final JsonValue config;

    public MongoJsonDBCUDItemCommandCreator(JsonValue config) {
        this.config = config;
    }

    @Override
    public JsonObject process(JsonObject requestContext) {
        JsonValue collection = config.get("operation").get(X_DCENTB).get("mongodb").get("collection");

        JsonObject mongoCommand;
        JsonObject mongoItem = requestContext.get("mongoItem", JsonValue.NULL).getJsonObject();

        if (mongoItem == null) {

            JsonValue itemId = requestContext.get("itemId");

            mongoCommand = JsonObject.EMPTY
                    .put("delete", JsonObject.EMPTY
                            .put("collection", collection)
                            .put("deletes", JsonArray.of(
                                    JsonObject.EMPTY
                                            .put("q", JsonObject.EMPTY
                                                    .put("_id", JsonArray.of(JsonObject.EMPTY
                                                            .put("$eq", itemId))))
                                            .put("limit", 1) // There should never be more than one item with the same itemId
                            )));
        } else {
            mongoCommand = JsonObject.EMPTY.put("update", JsonObject.EMPTY
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
                                    .put("upsert", true))));
        }

        return requestContext
                .put("mongoCommand", mongoCommand);
    }

}
