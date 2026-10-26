package com.zuunr.dcentb.rest.requesthandler.html;

import com.zuunr.dcentb.rest.controller.RequestHandlerBase;
import com.zuunr.dcentb.rest.processor.Processor;
import com.zuunr.dcentb.rest.processor.accesscontrol.AuthenticationProcessor;
import com.zuunr.dcentb.rest.processor.html.CollectionsIndexBuilder;
import com.zuunr.json.JsonValue;

/** "GET /ui" - the dashboard listing every visible collection. See html-ui-rendering-design.md section 2. */
public class CollectionsIndexRequestHandler extends RequestHandlerBase {

    private final Processor[] processors;

    public CollectionsIndexRequestHandler(JsonValue config) {
        super(config);
        processors = new Processor[]{
                config.as(AuthenticationProcessor.class),
                config.as(CollectionsIndexBuilder.class)
        };
    }

    @Override
    public Processor[] getProcessors() {
        return processors;
    }
}
