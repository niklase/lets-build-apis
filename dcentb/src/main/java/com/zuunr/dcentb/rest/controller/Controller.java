package com.zuunr.dcentb.rest.controller;

import com.zuunr.dcentb.rest.Request;
import com.zuunr.dcentb.rest.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author Niklas Eldberger
 */

@Component
public class Controller {

    private final RequestHandlerProvider requestHandlerProvider;

    @Autowired
    public Controller(RequestHandlerProvider endpointConfigProvider) {
        this.requestHandlerProvider = endpointConfigProvider;
    }

    public Response execute(Request request) {

        RequestHandler requestHandler = requestHandlerProvider.getRequestHandler(request);
        return requestHandler.process(request);
    }
}