package com.zuunr.dcentb.rest.processor;

import com.zuunr.dcentb.rest.controller.RequestHandlerConfig;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;

public abstract class Processor {

    public static final String X_DCENTB = "x-dcentb";
    public static final String REQUEST = "request";
    public static final String RESPONSE = "response";
    public static final String MONGODB = "mongodb";

    protected RequestHandlerConfig requestHandlerConfig;

    public abstract JsonObject process(JsonObject requestContext);

    public Processor(JsonValue config){
        requestHandlerConfig = config.as(RequestHandlerConfig.class);
    }

    @Override
    public String toString(){
        return getClass().getSimpleName();
    }
}
