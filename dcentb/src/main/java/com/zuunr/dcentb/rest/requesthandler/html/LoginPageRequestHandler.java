package com.zuunr.dcentb.rest.requesthandler.html;

import com.zuunr.dcentb.rest.controller.RequestHandlerBase;
import com.zuunr.dcentb.rest.processor.Processor;
import com.zuunr.dcentb.rest.processor.html.LoginFormRenderer;
import com.zuunr.json.JsonValue;

/** "GET /ui/login" - deliberately no AuthenticationProcessor: must be reachable with no credential at all. */
public class LoginPageRequestHandler extends RequestHandlerBase {

    private final Processor[] processors;

    public LoginPageRequestHandler(JsonValue config) {
        super(config);
        processors = new Processor[]{config.as(LoginFormRenderer.class)};
    }

    @Override
    public Processor[] getProcessors() {
        return processors;
    }
}
