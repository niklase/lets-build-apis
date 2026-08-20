package com.zuunr.dcentb.rest.processor;

import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;

public class DatabaseCommandResponseVerifier extends Processor {

    public DatabaseCommandResponseVerifier(JsonValue config) {
        super(config);
    }

    @Override
    public JsonObject process(JsonObject requestContext) {
        if (requestContext.get("mongoCommand") != null) {
            JsonObject mongoResult = requestContext.get("mongoResult", JsonValue.NULL).getJsonObject();
            if (!mongoResult.get("ok").getInteger().equals(1)) {
                return requestContext.put("response", JsonObject.EMPTY.put("status", 500));
            } else {
                JsonArray writeErrors = mongoResult.get("writeErrors", JsonValue.NULL).getJsonArray();
                if (writeErrors != null && !writeErrors.isEmpty()) {
                    int code = writeErrors.get(0).get("code").getInteger();
                    int status = code == 11000 ? 409 : 500;
                    return requestContext.put("response", JsonObject.EMPTY.put("status", status));
                }
            }
        }

        return requestContext;
    }
}
