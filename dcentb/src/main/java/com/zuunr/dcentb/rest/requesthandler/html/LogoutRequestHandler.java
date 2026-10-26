package com.zuunr.dcentb.rest.requesthandler.html;

import com.zuunr.dcentb.rest.controller.RequestHandlerBase;
import com.zuunr.dcentb.rest.processor.Processor;
import com.zuunr.dcentb.rest.processor.html.LogoutProcessor;
import com.zuunr.json.JsonValue;

/** "POST /ui/logout" - clears the session cookie; reachable regardless of current auth state. */
public class LogoutRequestHandler extends RequestHandlerBase {

    private final Processor[] processors;

    public LogoutRequestHandler(JsonValue config) {
        super(config);
        processors = new Processor[]{config.as(LogoutProcessor.class)};
    }

    @Override
    public Processor[] getProcessors() {
        return processors;
    }
}
