package com.zuunr.dcentb.rest.processor;

import com.zuunr.json.JsonObject;

public interface Processor {

    public static final String X_DCENTB = "x-decentb";
    public static final String REQUEST = "request";
    public static final String RESPONSE = "response";

    public JsonObject process(JsonObject requestContext);

}
