package com.zuunr.dcentb.rest.processor.accesscontrol;

import com.zuunr.dcentb.rest.processor.Processor;
import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;

public class ApiKeyAuthenticator implements Processor {


    JsonObject apiKeys = JsonObject.EMPTY.put("KEY1234", JsonObject.EMPTY.put("userId", "user1234"));

    public ApiKeyAuthenticator(JsonValue config) {}

    @Override
    public JsonObject process(JsonObject requestContext){
        JsonObject request = requestContext.get(REQUEST).getJsonObject();
        String apiKey = request.get("headers").get("api-key", JsonArray.of("KEY1234")).get(0).getString();
        JsonObject authentication = apiKeys.get(apiKey, JsonValue.NULL).getJsonObject();
        if (authentication == null) {
            return requestContext.put(RESPONSE, JsonObject.EMPTY.put("status", 401).put("message", "Invalid api-key"));
        }

        return requestContext.put("authentication", authentication);
    }
}
