package com.zuunr.dcentb.rest.controller;

import com.zuunr.dcentb.rest.Request;
import com.zuunr.dcentb.rest.Response;
import com.zuunr.json.JsonObject;

public class RequestHandlerHandle {
    private RequestHandler requestHandler;
    private JsonObject bicatch;

    /**
     *
     * @param requestHandler
     * @param bicatch - data that was captured in the process of determine which RequestHandler should be used for the request, i.e. pathParameters
     */
    public RequestHandlerHandle(RequestHandler requestHandler, JsonObject bicatch) {
        this.requestHandler = requestHandler;
        this.bicatch = bicatch;
    }

    public Response runRequestHandler(Request request) {
        return requestHandler.process(new Request(request.asJsonObject().putAll(bicatch)));
    }
}


