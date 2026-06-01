package com.zuunr.dcentb.rest.processor;

import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;

public class VerifyMongoCommandExecution implements Processor {

    public VerifyMongoCommandExecution(JsonValue config) {
    }

    @Override
    public JsonObject process(JsonObject requestContext) {
        if (requestContext.get("mongoCommand") != null) {
            JsonObject mongoResult = requestContext.get("mongoResult", JsonValue.NULL).getJsonObject();
            if (!mongoResult.get("ok").getInteger().equals(1)) {
                return requestContext.put("response", JsonObject.EMPTY.put("status", 500));
            }
        }

        return requestContext;
    }
}
