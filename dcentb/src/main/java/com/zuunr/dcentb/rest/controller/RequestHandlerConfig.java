package com.zuunr.dcentb.rest.controller;

import com.zuunr.dcentb.rest.requesthandler.CreateItemRequestHandler;
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
            }
            if (method.equalsIgnoreCase("post")) {
                requestHandler = config.as(CreateItemRequestHandler.class);
            }
        }
        return requestHandler;
    }



     public OperationConfig getOperationConfig(){
        return config.get("operation").as(OperationConfig.class);
     }

}
