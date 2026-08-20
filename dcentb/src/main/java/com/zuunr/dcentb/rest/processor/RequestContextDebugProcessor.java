package com.zuunr.dcentb.rest.processor;

import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;

public class RequestContextDebugProcessor extends Processor {

    RequestContextDebugProcessor(JsonValue config){
        super(config);
    }

    @Override
    public JsonObject process(JsonObject requestContext) {
        return JsonObject.EMPTY.put(RESPONSE, JsonObject.EMPTY.put("status", 200).put("body", JsonObject.EMPTY.put("requestContext", requestContext)));
    }
}
