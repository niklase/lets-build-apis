package com.zuunr.dcentb.rest.processor.accesscontrol;

import com.zuunr.dcentb.rest.processor.Processor;
import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import com.zuunr.json.util.StringSplitter;

public class ApiKeyAuthenticator implements Processor {

    JsonObject config;

    JsonObject apiKeys = JsonObject.EMPTY.put("KEY1234", JsonObject.EMPTY.put("userId", "user1234"));

    public ApiKeyAuthenticator(JsonValue config) {
        this.config = config.getJsonObject();
    }

    @Override
    public JsonObject process(JsonObject requestContext) {
        JsonObject request = requestContext.get(REQUEST).getJsonObject();
        String apiKey = request.get("headers", JsonObject.EMPTY).get("api-key", JsonArray.of("")).get(0).getString();


        boolean valid = config.get("x-dcentb", JsonObject.EMPTY)
                                .get("accessControl", JsonObject.EMPTY)
                                .get("apiKeys", JsonObject.EMPTY)
                                .get(apiKey) != null;

        if (!valid) {
            return requestContext.put(RESPONSE, JsonObject.EMPTY.put("status", 401).put("message", "Invalid api-key"));
        }

        JsonObject apiKeyInfo = parseApiKey(apiKey);
        JsonValue userId = apiKeyInfo.get("userId");

        //JsonObject authentication = apiKeys.get(apiKey, JsonValue.NULL).getJsonObject();
        if (userId == null) {
            return requestContext.put(RESPONSE, JsonObject.EMPTY.put("status", 401).put("message", "Invalid api-key"));
        }

        return requestContext.put("authenticatedUser", apiKeyInfo);
    }

    private JsonObject parseApiKey(String apiKey) {
        JsonArray split = StringSplitter.splitString(apiKey, '_');
        String userId = split.get(1).getString();
        JsonArray permissions = StringSplitter.splitString(split.get(2).getString(), '-');
        return JsonObject.EMPTY.put("userId", userId).put("permissions", permissions);
    }
}
