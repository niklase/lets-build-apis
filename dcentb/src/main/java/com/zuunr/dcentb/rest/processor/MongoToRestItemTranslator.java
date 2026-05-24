package com.zuunr.dcentb.rest.processor;

import com.zuunr.api.openapi.JsonUri;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;

public class MongoToRestItemTranslator implements Processor {

    public MongoToRestItemTranslator(JsonValue config) {
    }

    @Override
    public JsonObject process(JsonObject requestContext) {
        JsonObject mongoResult = requestContext.get("mongoResult", JsonObject.EMPTY).getJsonObject();

        if (!mongoResult.get("ok").getInteger().equals(1)) {
            return requestContext.put("response", JsonObject.EMPTY.put("status", 500));
        }

        JsonObject mongoItem = requestContext.get("mongoCommand").get("insert").get("documents").get(0).getJsonObject();

        JsonObject item = com.zuunr.dcentb.rest.processor.mongo.MongoToRestItemTranslator.getRestItem(mongoItem);

        JsonUri requestUri = requestContext.get(REQUEST).get("uri").as(JsonUri.class);
        String basePath = requestUri.getPath().getString();

        return requestContext.put("response", JsonObject.EMPTY
                .put("status", 201)
                .put("headers", JsonObject.EMPTY.put("location", basePath + "/" + item.get("meta").get("id").getString()))
                .put("body", item));
    }
}
