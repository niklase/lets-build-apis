package com.zuunr.dcentb.rest.processor;

import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonObjectBuilder;
import com.zuunr.json.JsonValue;

/**
 * itemId and newState will be set on requestContext
 */

public class IdempotentPutResponseCreator extends Processor {

    public IdempotentPutResponseCreator(JsonValue config) {
        super(config);
    }

    @Override
    public JsonObject process(JsonObject requestContext) {

        JsonObject request = requestContext.get("request", JsonObject.EMPTY).getJsonObject();
        //String method = request.get("method").getString().toUpperCase();

        JsonValue currentState = requestContext.get("currentState");

        JsonObjectBuilder responseBuilder = JsonObject.EMPTY.builder();

        if (requestHandlerConfig.isMethod("PUT") && !currentState.isNull()) {
            responseBuilder.put("body", currentState);
            responseBuilder.put("status", 200);
            requestContext = requestContext.put("response", responseBuilder.build());
        }

        return requestContext;
    }
}
