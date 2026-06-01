package com.zuunr.dcentb.rest.processor;

import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;

public class CreateNewStateFromRequest implements Processor{

    public CreateNewStateFromRequest(JsonValue config) {}

    @Override
    public JsonObject process(JsonObject requestContext) {

        JsonObject request = requestContext.get("request").getJsonObject();
        requestContext = requestContext
                .put("newState", request.get("body"));

        JsonValue id =  request.get("pathParameters", JsonObject.EMPTY).get("id");

        if (id != null) {
            requestContext = requestContext.put("itemId", id);
        }
        return requestContext;
    }
}
