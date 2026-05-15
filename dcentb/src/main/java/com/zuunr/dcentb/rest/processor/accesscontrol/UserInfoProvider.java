package com.zuunr.dcentb.rest.processor.accesscontrol;

import com.zuunr.dcentb.rest.processor.Processor;
import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;

public class UserInfoProvider implements Processor {

    private static JsonObject users = JsonObject.EMPTY
            .put("user1234", JsonObject.EMPTY
                    .put("userPermissions", JsonArray.of("ADMIN")) // Should be determined via user role
            );

    public UserInfoProvider(JsonValue config){}

    @Override
    public JsonObject process(JsonObject requestContext) {
        JsonObject authentication = requestContext.get("authentication").getJsonObject();
        JsonObject userInfo = users.get(authentication.get("userId").getString()).getJsonObject();
        return requestContext.put("authenticatedUser", authentication.putAll(userInfo));
    }
}