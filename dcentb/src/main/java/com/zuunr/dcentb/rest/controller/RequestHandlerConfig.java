package com.zuunr.dcentb.rest.controller;

import com.zuunr.dcentb.rest.requesthandler.CUDItemRequestHandler;
import com.zuunr.dcentb.rest.requesthandler.ReadCollectionRequestHandler;
import com.zuunr.dcentb.rest.requesthandler.ReadItemRequestHandler;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;

public class RequestHandlerConfig {

    private JsonObject config;
    private RequestHandler requestHandler;
    private String method;


    public RequestHandlerConfig(JsonValue config) {
        this.config = config.getJsonObject();
    }

    public RequestHandler getRequestHandler() {

        if (requestHandler == null) {
            initRequestHandler();
        }
        return requestHandler;
    }

    private void initRequestHandler() {
        method = config.get("method").getString().toUpperCase();

        String path = config.get("path").getString();

        if (isMethod("get")) {

            if (path.matches(".*/\\{[^}]+\\}$")) {
                requestHandler = config.as(ReadItemRequestHandler.class);
            } else {
                requestHandler = config.as(ReadCollectionRequestHandler.class);
            }
        } else if (isMethod("post")) {
            if (path.endsWith("/getCollection")) {
                requestHandler = config.as(ReadCollectionRequestHandler.class);
            } else {
                requestHandler = config.as(CUDItemRequestHandler.class);
            }
        } else if (isMethod("put")) {
            requestHandler = config.as(CUDItemRequestHandler.class);
        } else if (isMethod("patch")) {
            requestHandler = config.as(CUDItemRequestHandler.class);
        } else if (isMethod("delete")) {
            requestHandler = config.as(CUDItemRequestHandler.class);
        }
    }


    public OperationConfig getOperationConfig() {
        return config.get("operation").as(OperationConfig.class);
    }


    public boolean isMethod(String method) {
        return this.method.equals(method.toUpperCase());
    }

    public final JsonValue asJsonValue(){
        return config.jsonValue();
    }
}
