package com.zuunr.dcentb.rest.processor;

import com.zuunr.api.openapi.OAS3Deserializer;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;

public class OASRequestProcessor implements Processor {

    private JsonValue config;

    public OASRequestProcessor(JsonValue config) {
        this.config = config;
    }

    @Override
    public JsonObject process(JsonObject requestContext) {

        JsonObject result = OAS3Deserializer.deserializeRequest(requestContext, config.get("operation").getJsonObject());
        if (result.get("ok").getBoolean()) {
            return requestContext.put(REQUEST, result.get(REQUEST));
        } else {
            return requestContext
                    .put(RESPONSE, JsonObject.EMPTY
                            .put("status", 400)
                            .put("body", JsonObject.EMPTY
                                    .put("errors", result.get("errors"))));
        }
    }

}
