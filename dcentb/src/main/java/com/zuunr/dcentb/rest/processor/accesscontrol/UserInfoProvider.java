package com.zuunr.dcentb.rest.processor.accesscontrol;

import com.zuunr.dcentb.rest.processor.Processor;
import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;

public class UserInfoProvider implements Processor {

    private JsonObject config;

    private static JsonObject users = JsonObject.EMPTY
            .put("user1234", JsonObject.EMPTY
                    .put("userPermissions", JsonArray.of("ADMIN")) // Should be determined via user role
            );

    public UserInfoProvider(JsonValue config){
        this.config = config.getJsonObject();
    }

    @Override
    public JsonObject process(JsonObject requestContext) {

        JsonObject authenticatedUser = requestContext.get("authenticatedUser", JsonObject.EMPTY).getJsonObject();
        JsonValue userId = requestContext.get("authenticatedUser", JsonObject.EMPTY).get("userId");
        if (userId == null) {
            return JsonObject.EMPTY.put("repsonse", JsonObject.EMPTY.put("status", 401));
        }

        JsonObject userInfo = config.get("x-dcentb", JsonObject.EMPTY).get("users", JsonObject.EMPTY).get(userId.getString(), JsonObject.EMPTY).getJsonObject();

        return requestContext.put("authenticatedUser", userInfo.putAll(authenticatedUser));
    }
}