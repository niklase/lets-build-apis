package com.zuunr.dcentb.rest.processor;

import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonObjectBuilder;
import com.zuunr.json.JsonValue;

/**
 * itemId and newState will be set on requestContext.
 *
 * Runs after StateTransitionValidator, which is what actually decides whether a PUT into an
 * existing item is allowed to reach this class at all: for PUT, StateTransitionValidator
 * compares currentState to newState (ignoring "meta") and produces a 409 itself when they
 * differ. So by the time this class sees a non-null currentState for a PUT, the request body
 * is already known to match what's stored - this is not a blind "item exists, return it
 * unchanged" - and returning currentState as the 200 body is reporting exactly what the
 * client just asked to (re-)create, not stale or unrelated data.
 */

public class IdempotentPutResponseCreator extends Processor {

    public IdempotentPutResponseCreator(JsonValue config) {
        super(config);
    }

    @Override
    public JsonObject process(JsonObject requestContext) {

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
