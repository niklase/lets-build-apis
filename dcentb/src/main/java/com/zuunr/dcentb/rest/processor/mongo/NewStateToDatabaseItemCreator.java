package com.zuunr.dcentb.rest.processor.mongo;

import com.zuunr.dcentb.rest.processor.Processor;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;

public class NewStateToDatabaseItemCreator extends Processor {

    private final String path;

    public NewStateToDatabaseItemCreator(JsonValue config) {
        path = config.get("path").getString();
    }

    @Override
    public JsonObject process(JsonObject requestContext) {

        JsonValue newStateJsonValue = requestContext.get("newState");

        if (!newStateJsonValue.isNull()) {

            JsonObject newState = newStateJsonValue.getJsonObject();

            JsonObject meta = newState.get("meta", JsonObject.EMPTY).getJsonObject();
            JsonObject mongoItem = newState
                    .put("_id", meta.get("id"))
                    .put("meta", meta.remove("id"));

            requestContext = requestContext.put("mongoItem", mongoItem);
        }
        return requestContext;
    }
}
