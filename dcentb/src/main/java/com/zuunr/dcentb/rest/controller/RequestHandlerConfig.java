package com.zuunr.dcentb.rest.controller;

import com.zuunr.dcentb.rest.requesthandler.CUDItemRequestHandler;
import com.zuunr.dcentb.rest.requesthandler.ReadCollectionRequestHandler;
import com.zuunr.dcentb.rest.requesthandler.ReadItemRequestHandler;
import com.zuunr.dcentb.rest.requesthandler.html.CollectionsIndexRequestHandler;
import com.zuunr.dcentb.rest.requesthandler.html.LoginPageRequestHandler;
import com.zuunr.dcentb.rest.requesthandler.html.LoginSubmitRequestHandler;
import com.zuunr.dcentb.rest.requesthandler.html.LogoutRequestHandler;
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

        // The HTML UI's own framework pages (html-ui-rendering-design.md sections 2, 8) -
        // fixed, well-known paths, checked before the generic method-based dispatch below
        // so they're never mistaken for a declared API collection's own GET/POST.
        if (isMethod("get") && "/ui".equals(path)) {
            requestHandler = config.as(CollectionsIndexRequestHandler.class);
        } else if (isMethod("get") && "/ui/login".equals(path)) {
            requestHandler = config.as(LoginPageRequestHandler.class);
        } else if (isMethod("post") && "/ui/login".equals(path)) {
            requestHandler = config.as(LoginSubmitRequestHandler.class);
        } else if (isMethod("post") && "/ui/logout".equals(path)) {
            requestHandler = config.as(LogoutRequestHandler.class);
        } else if (isMethod("get")) {

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
