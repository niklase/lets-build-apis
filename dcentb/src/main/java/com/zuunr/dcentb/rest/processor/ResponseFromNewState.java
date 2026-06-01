package com.zuunr.dcentb.rest.processor;

import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonObjectBuilder;
import com.zuunr.json.JsonValue;

/**
 * itemId and newState will be set on requestContextß
 */

public class ResponseFromNewState implements Processor {

    public ResponseFromNewState(JsonValue config){}

    @Override
    public JsonObject process(JsonObject requestContext) {

        JsonObject request = requestContext.get("request", JsonObject.EMPTY).getJsonObject();
        String method = request.get("method").getString().toUpperCase();

        JsonValue newState = requestContext.get("newState");

        JsonObjectBuilder responseBuilder = JsonObject.EMPTY.builder();

        if (method.equals("POST")) {

            responseBuilder.put("body", newState);
            responseBuilder.put("headers", JsonObject.EMPTY
                    .put("location", JsonArray.of(newState.get("meta").get("href"))));
            responseBuilder.put("status", 201);
        } else if (method.equals("PATCH")) {
            responseBuilder.put("body", newState);
            responseBuilder.put("status", 200);
        }

        if (newState == null || newState.isNull()) {
            responseBuilder.put("status", 204);
        }

        return requestContext.put("response", responseBuilder.build());
    }
}
