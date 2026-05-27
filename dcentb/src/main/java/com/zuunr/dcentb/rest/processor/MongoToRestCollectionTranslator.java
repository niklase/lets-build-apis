package com.zuunr.dcentb.rest.processor;

import com.zuunr.dcentb.rest.processor.mongo.MongoToApiItemTranslator;
import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonArrayBuilder;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;

public class MongoToRestCollectionTranslator implements Processor {

    public MongoToRestCollectionTranslator(JsonValue config) {
    }

    @Override
    public JsonObject process(JsonObject requestContext) {
        JsonObject mongoResult = requestContext.get("mongoResult", JsonObject.EMPTY).getJsonObject();

        if (!mongoResult.get("ok").getInteger().equals(1)) {
            return requestContext.put("response", JsonObject.EMPTY.put("status", 500));
        }

        JsonArrayBuilder itemsBuilder = JsonArray.EMPTY.builder();
        for (JsonValue item : mongoResult.get("cursor").get("firstBatch").getJsonArray()) {
            itemsBuilder.add(MongoToApiItemTranslator.getRestItem(item.getJsonObject()));
        }
        return requestContext
                .put("response", JsonObject.EMPTY
                        .put("status", 200)
                        .put("body", JsonObject.EMPTY
                                .put("items", itemsBuilder.build())));
    }
}