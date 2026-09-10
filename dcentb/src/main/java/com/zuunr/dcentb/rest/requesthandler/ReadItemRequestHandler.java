package com.zuunr.dcentb.rest.requesthandler;

import com.zuunr.dcentb.rest.controller.RequestHandlerBase;
import com.zuunr.dcentb.rest.processor.*;
import com.zuunr.dcentb.rest.processor.accesscontrol.AuthenticationProcessor;
import com.zuunr.dcentb.rest.processor.accesscontrol.CurrentStateAccessController;
import com.zuunr.dcentb.rest.processor.accesscontrol.RequestAccessController;
import com.zuunr.dcentb.rest.processor.accesscontrol.UserInfoProvider;
import com.zuunr.dcentb.rest.processor.apimodel.CurrentStateItemDecorator;
import com.zuunr.dcentb.rest.processor.mongo.DatabaseCommandReadCreator;
import com.zuunr.dcentb.rest.processor.mongo.DatabaseCommandRunner;
import com.zuunr.json.JsonValue;

public class ReadItemRequestHandler extends RequestHandlerBase {

    private Processor[] processors;

    public ReadItemRequestHandler(JsonValue config) {
        super(config);

        processors = new Processor[]{
                config.as(AuthenticationProcessor.class),
                config.as(OASRequestDeserializer.class),
                config.as(UserInfoProvider.class),
                config.as(RequestAccessController.class),
                config.as(DatabaseCommandReadCreator.class),            // create new state (get current state)
                config.as(DatabaseCommandRunner.class),                 // create new state (get current state)
                config.as(DatabaseCommandResponseVerifier.class),       // create new state (get current state -> item)
                config.as(CurrentStateFromDatabaseApplier.class),       // state from mongo
                config.as(CurrentStateItemDecorator.class),             // decorates currentState
                config.as(CurrentStateAccessController.class),          // verify if operation is authorized with current state
                config.as(CurrentStateResponseCreator.class),           // itemId={id} and currentState from DB
        };
    }

    @Override
    public Processor[] getProcessors() {
        return processors;
    }
}
