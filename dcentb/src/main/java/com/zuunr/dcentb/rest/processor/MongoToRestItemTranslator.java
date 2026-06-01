package com.zuunr.dcentb.rest.processor;

import com.zuunr.dcentb.rest.processor.mongo.MongoToApiItemTranslator;
import com.zuunr.json.JsonArray;
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

        JsonArray firstBatch = mongoResult.get("cursor").get("firstBatch").getJsonArray();
        if (firstBatch == null || firstBatch.isEmpty()) {
            return requestContext.put("response", JsonObject.EMPTY.put("status", 404));
        }

        JsonObject item = MongoToApiItemTranslator.getRestItem(firstBatch.get(0).getJsonObject());
        return requestContext
                .put("response", JsonObject.EMPTY
                        .put("status", 200)
                        .put("body", item));
    }
}