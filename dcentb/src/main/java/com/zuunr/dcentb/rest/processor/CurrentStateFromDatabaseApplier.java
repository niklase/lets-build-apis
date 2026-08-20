package com.zuunr.dcentb.rest.processor;

import com.zuunr.dcentb.rest.processor.mongo.MongoToApiItemTranslator;
import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;

public class CurrentStateFromDatabaseApplier extends Processor {


    private boolean _404ForCurrentStateNull;
    private MongoToApiItemTranslator mongoToApiItemTranslator;

    public CurrentStateFromDatabaseApplier(JsonValue config) {
        super(config);
        String method = config.get("method").getString();
        _404ForCurrentStateNull = method.equals("patch") || method.equals("delete");
    }

    @Override
    public JsonObject process(JsonObject requestContect) {
        JsonValue currentState = requestContect
                .get("mongoResult", JsonObject.EMPTY)
                .get("cursor", JsonObject.EMPTY)
                .get("firstBatch", JsonArray.of(JsonValue.NULL))
                .getJsonArray()
                .get(0, JsonValue.NULL);

        if (_404ForCurrentStateNull && currentState.isNull()) {
            return requestContect
                    .put("response", JsonObject.EMPTY
                            .put("status", 404)
                            .put("body", JsonObject.EMPTY)
                    );
        }

        return requestContect.put("currentState", currentState.isNull()
                ? currentState :
                MongoToApiItemTranslator.getRestItem(currentState.getJsonObject()).jsonValue());
    }
}
