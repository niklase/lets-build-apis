package com.zuunr.dcentb.rest.processor;

import com.zuunr.dcentb.rest.Request;
import com.zuunr.dcentb.rest.Response;
import com.zuunr.dcentb.rest.controller.RequestHandler;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;

public class ReadCollectionRequestHandler implements RequestHandler {

    private AuthenticationProcessor authenticationProcessor;
    private Processor[] processors;

    public ReadCollectionRequestHandler(JsonValue config) {
        AuthenticationProcessor authenticationProcessor = config.as(AuthenticationProcessor.class);
        OASRequestProcessor oasRequestProcessor = config.as(OASRequestProcessor.class);
        processors = new Processor[]{authenticationProcessor, oasRequestProcessor};
    }

    public Response process(Request request) {

        JsonObject updatedRequestContext = JsonObject.EMPTY.put(Processor.REQUEST, request.asJsonObject());
        for (Processor processor : processors) {
            updatedRequestContext = processor.process(updatedRequestContext);
            JsonObject response = updatedRequestContext.get(Processor.RESPONSE, JsonValue.NULL).getJsonObject();
            if (response != null) {
                return response.as(Response.class);
            }
        }
        return JsonObject.EMPTY.put("status", 500).as(Response.class);
    }
}
