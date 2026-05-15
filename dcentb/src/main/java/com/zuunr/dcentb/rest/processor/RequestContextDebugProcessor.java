package com.zuunr.dcentb.rest.processor;

import com.zuunr.json.JsonObject;

public class RequestContextDebugProcessor implements Processor {

    @Override
    public JsonObject process(JsonObject requestContext) {
        return JsonObject.EMPTY.put(RESPONSE, JsonObject.EMPTY.put("status", 200).put("body", JsonObject.EMPTY.put("requestContext", requestContext)));
    }
}
