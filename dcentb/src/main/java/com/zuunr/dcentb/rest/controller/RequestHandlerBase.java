package com.zuunr.dcentb.rest.controller;

import com.zuunr.dcentb.rest.Request;
import com.zuunr.dcentb.rest.Response;
import com.zuunr.dcentb.rest.processor.Processor;
import com.zuunr.dcentb.rest.processor.accesscontrol.ResponseAccessController;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import org.slf4j.Logger;

public abstract class RequestHandlerBase implements RequestHandler {

    Logger logger = org.slf4j.LoggerFactory.getLogger(RequestHandlerBase.class);

    public ResponseAccessController responseAccessController;

    public abstract Processor[] getProcessors();


    public RequestHandlerBase(JsonValue config) {
        responseAccessController = new ResponseAccessController(config);
    }


    @Override
    public Response process(Request request) {
        Response response = processInternally(request);
        logger.info("Response: {}", response.toString());
        return response;
    }

    public Response processInternally(Request request) {
        try {
            JsonObject updatedRequestContext = JsonObject.EMPTY.put(Processor.REQUEST, request.asJsonObject());
            for (Processor processor : getProcessors()) {
                logger.info("{} requestContext: {}", processor.getClass().getSimpleName(), updatedRequestContext.toString());
                updatedRequestContext = processor.process(updatedRequestContext);
                JsonObject response = updatedRequestContext.get(Processor.RESPONSE, JsonValue.NULL).getJsonObject();
                if (response != null) {
                    logger.info("{} requestContext: {}", processor.getClass().getSimpleName(), updatedRequestContext.toString());
                    updatedRequestContext = responseAccessController.process(updatedRequestContext);
                    response = updatedRequestContext.get(Processor.RESPONSE, JsonValue.NULL).getJsonObject();
                    return response.as(Response.class);
                }
            }
        } catch (Exception e) {
            logger.error("processInternally exception", e);
        }
        return JsonObject.EMPTY.put("status", 500).as(Response.class);
    }
}
