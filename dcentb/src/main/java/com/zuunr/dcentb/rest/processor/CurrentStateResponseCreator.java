package com.zuunr.dcentb.rest.processor;

import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonObjectBuilder;
import com.zuunr.json.JsonValue;

/**
 * itemId and newState will be set on requestContext
 */

public class CurrentStateResponseCreator extends Processor {

    public CurrentStateResponseCreator(JsonValue config) {
        super(config);
    }

    @Override
    public JsonObject process(JsonObject requestContext) {


        JsonValue currentState = requestContext.get("currentState");

        JsonObjectBuilder responseBuilder = JsonObject.EMPTY.builder();

        if (currentState == null || currentState.isNull()) {
            responseBuilder.put("status", 404);
        } else {
            responseBuilder.put("body", currentState);
            responseBuilder.put("status", 200);
        }

        return requestContext.put("response", responseBuilder.build());
    }
}
