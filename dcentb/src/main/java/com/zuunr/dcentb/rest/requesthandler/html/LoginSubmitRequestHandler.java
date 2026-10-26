package com.zuunr.dcentb.rest.requesthandler.html;

import com.zuunr.dcentb.rest.controller.RequestHandlerBase;
import com.zuunr.dcentb.rest.processor.Processor;
import com.zuunr.dcentb.rest.processor.html.LoginSubmitProcessor;
import com.zuunr.json.JsonValue;

/** "POST /ui/login" - validates the submitted api-key itself; no AuthenticationProcessor in this chain either. */
public class LoginSubmitRequestHandler extends RequestHandlerBase {

    private final Processor[] processors;

    public LoginSubmitRequestHandler(JsonValue config) {
        super(config);
        processors = new Processor[]{config.as(LoginSubmitProcessor.class)};
    }

    @Override
    public Processor[] getProcessors() {
        return processors;
    }
}
