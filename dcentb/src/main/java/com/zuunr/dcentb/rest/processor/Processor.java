package com.zuunr.dcentb.rest.processor;

import com.zuunr.json.JsonObject;

public abstract class Processor {

    public static final String X_DCENTB = "x-dcentb";
    public static final String REQUEST = "request";
    public static final String RESPONSE = "response";
    public static final String MONGODB = "mongodb";

    public abstract JsonObject process(JsonObject requestContext);


    @Override
    public String toString(){
        return getClass().getSimpleName();
    }
}
