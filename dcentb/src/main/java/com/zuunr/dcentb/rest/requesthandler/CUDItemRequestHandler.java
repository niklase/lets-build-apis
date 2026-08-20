package com.zuunr.dcentb.rest.requesthandler;

import com.zuunr.dcentb.rest.controller.RequestHandlerBase;
import com.zuunr.dcentb.rest.processor.*;
import com.zuunr.dcentb.rest.processor.accesscontrol.*;
import com.zuunr.dcentb.rest.processor.apimodel.NewStateCreator;
import com.zuunr.dcentb.rest.processor.mongo.DatabaseCommandRunner;
import com.zuunr.dcentb.rest.processor.mongo.DatabaseCommandReadCreator;
import com.zuunr.dcentb.rest.processor.mongo.NewStateToDatabaseItemCreator;
import com.zuunr.json.JsonValue;

public class CUDItemRequestHandler extends RequestHandlerBase {

    private Processor[] processors;

    public CUDItemRequestHandler(JsonValue config) {
        super(config);

        processors = new Processor[] {
                config.as(ApiKeyAuthenticator.class),
                config.as(OASRequestDeserializer.class),
                config.as(UserInfoProvider.class),
                config.as(RequestAccessController.class),
                config.as(DatabaseCommandReadCreator.class),            // create new state (get current state)
                config.as(DatabaseCommandRunner.class),                 // create new state (get current state)
                config.as(DatabaseCommandResponseVerifier.class),       // create new state (get current state -> item)
                config.as(CurrentStateFromDatabaseApplier.class),       // state from mongo
                config.as(CurrentStateAccessController.class),          // verify if operation is authorized with current state
                config.as(NewStateCreator.class),                       // current state + new state (from mongo or from apiModel)
                config.as(StateTransitionValidator.class),
                config.as(IdempotentPutResponseCreator.class),          // Returns 200 of repeated put
                config.as(NewStateToDatabaseItemCreator.class),         // new state -> mongo item
                config.as(DatabaseCUDItemCommandCreator.class),         // mongoitem -> mongo command
                config.as(DatabaseCommandRunner.class),                 // run mongo command
                config.as(DatabaseCommandResponseVerifier.class),
                config.as(NewStateResponseCreator.class),               // itemId={id} and newState={request.body}
        };
    }

    @Override
    public Processor[] getProcessors() {
        return processors;
    }
}
