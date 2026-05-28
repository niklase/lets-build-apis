package com.zuunr.dcentb.rest.controller;

import com.zuunr.dcentb.rest.requesthandler.CreateItemRequestHandler;
import com.zuunr.dcentb.rest.requesthandler.ReadCollectionRequestHandler;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;

public class OperationConfig {

    private JsonObject config;
    private RequestHandler requestHandler;

    public OperationConfig(JsonValue config) {
        this.config = config.getJsonObject();

    }

    public RequestHandler getRequestHandler() {

        if (requestHandler == null) {
            String method = config.get("method").getString();
            if (method.equalsIgnoreCase("get")) {
                requestHandler = config.as(ReadCollectionRequestHandler.class);
            }
            if (method.equalsIgnoreCase("post")) {
                requestHandler = config.as(CreateItemRequestHandler.class);
            }
        }
        return requestHandler;
    }
}
