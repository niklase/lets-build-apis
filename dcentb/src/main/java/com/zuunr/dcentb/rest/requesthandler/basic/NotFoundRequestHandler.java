package com.zuunr.dcentb.rest.requesthandler.basic;

import com.zuunr.dcentb.rest.Request;
import com.zuunr.dcentb.rest.Response;
import com.zuunr.dcentb.rest.controller.RequestHandler;
import com.zuunr.json.JsonObject;

public class NotFoundRequestHandler implements RequestHandler {
    @Override
    public Response process(Request request) {
        return new Response(JsonObject.EMPTY.put("status", 404));
    }
};
