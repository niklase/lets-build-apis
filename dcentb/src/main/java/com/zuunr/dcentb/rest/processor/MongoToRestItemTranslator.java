package com.zuunr.dcentb.rest.processor;

import com.zuunr.dcentb.rest.processor.mongo.MongoToApiItemTranslator;
import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;

public class MongoToRestItemTranslator extends Processor {

    public MongoToRestItemTranslator(JsonValue config) {
        super(config);
    }

    @Override
    public JsonObject process(JsonObject requestContext) {

        JsonObject request = requestContext.get("request").getJsonObject();
        JsonObject mongoResult = requestContext.get("mongoResult", JsonObject.EMPTY).getJsonObject();

        if (!mongoResult.get("ok").getInteger().equals(1)) {
            return requestContext.put("response", JsonObject.EMPTY.put("status", 500));
        }

        JsonArray firstBatch = mongoResult.get("cursor").get("firstBatch").getJsonArray();
        if (firstBatch == null || firstBatch.isEmpty()) {
            return requestContext.put("response", JsonObject.EMPTY.put("status", 404));
        }

        JsonObject currentState = MongoToApiItemTranslator.getRestItem(firstBatch.get(0).getJsonObject());

        requestContext = requestContext.put("currentState", currentState);

        if ("GET".equalsIgnoreCase(request.get("method").getString())) {
            requestContext = requestContext
                    .put("response", JsonObject.EMPTY
                            .put("status", 200)
                            .put("body", currentState));
        }
        return requestContext;
    }
}