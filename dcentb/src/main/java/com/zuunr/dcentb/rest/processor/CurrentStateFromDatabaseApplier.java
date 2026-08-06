package com.zuunr.dcentb.rest.processor;

import com.zuunr.dcentb.rest.processor.mongo.MongoToApiItemTranslator;
import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;

public class CurrentStateFromDatabaseApplier extends Processor {

    public CurrentStateFromDatabaseApplier(JsonValue config) {}

    @Override
    public JsonObject process(JsonObject requestContect) {
        JsonValue currentState = requestContect.get("mongoResult", JsonObject.EMPTY).get("cursor", JsonObject.EMPTY).get("firstBatch", JsonArray.of(JsonValue.NULL)).getJsonArray().get(0);
        return requestContect.put("currentState", currentState.isNull()
                ? currentState :
                MongoToApiItemTranslator.getRestItem(currentState.getJsonObject()).jsonValue());
    }
}
