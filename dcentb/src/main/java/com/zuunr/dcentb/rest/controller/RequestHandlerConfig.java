package com.zuunr.dcentb.rest.controller;

import com.zuunr.dcentb.rest.requesthandler.CUDItemRequestHandler;
import com.zuunr.dcentb.rest.requesthandler.ReadCollectionRequestHandler;
import com.zuunr.dcentb.rest.requesthandler.ReadItemRequestHandler;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;

public class RequestHandlerConfig {

    private JsonObject config;
    private RequestHandler requestHandler;

    public RequestHandlerConfig(JsonValue config) {
        this.config = config.getJsonObject();
    }

    public RequestHandler getRequestHandler() {

        if (requestHandler == null) {
            String method = config.get("method").getString();

            String path = config.get("path").getString();



            if (method.equalsIgnoreCase("get")) {

                if (path.matches(".*/\\{[^}]+\\}$")) {
                    requestHandler = config.as(ReadItemRequestHandler.class);
                } else {
                    requestHandler = config.as(ReadCollectionRequestHandler.class);
                }
            } else if (method.equalsIgnoreCase("post")) {
                if (path.endsWith("/getCollection")) {
                    requestHandler = config.as(ReadCollectionRequestHandler.class);
                } else {
                    requestHandler = config.as(CUDItemRequestHandler.class);
                }
            } else if (method.equalsIgnoreCase("patch")) {
                requestHandler = config.as(CUDItemRequestHandler.class);
            }
        }
        return requestHandler;
    }


    public OperationConfig getOperationConfig() {
        return config.get("operation").as(OperationConfig.class);
    }

}
