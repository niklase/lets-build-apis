package com.zuunr.dcentb.rest.processor.accesscontrol;

import com.zuunr.dcentb.rest.processor.Processor;
import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;

public class Authenticator extends Processor {

    private final boolean authRequired;

    public Authenticator(JsonValue config) {
        super(config);
        this.authRequired = !config.get("security", JsonArray.EMPTY).getJsonArray().isEmpty();
    }

    @Override
    public JsonObject process(JsonObject requestContext) {
        if (authRequired) {
            JsonObject request = requestContext.get(REQUEST).getJsonObject();
            JsonObject headers = request.get("headers").getJsonObject();
            JsonArray authorization = headers.get("authorization", JsonArray.EMPTY).getJsonArray();
            if (authorization.size() != 1) {
                return requestContext.put(RESPONSE, JsonObject.EMPTY.put("status", 401).put("message", "Invalid authorization header"));
            }
        }
        return requestContext;
    }
}
