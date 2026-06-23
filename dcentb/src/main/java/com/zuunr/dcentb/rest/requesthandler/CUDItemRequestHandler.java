package com.zuunr.dcentb.rest.requesthandler;

import com.zuunr.dcentb.rest.controller.RequestHandlerBase;
import com.zuunr.dcentb.rest.processor.*;
import com.zuunr.dcentb.rest.processor.accesscontrol.ApiKeyAuthenticator;
import com.zuunr.dcentb.rest.processor.accesscontrol.RequestAccessController;
import com.zuunr.dcentb.rest.processor.accesscontrol.UserInfoProvider;
import com.zuunr.dcentb.rest.processor.apimodel.UpdateNewState;
import com.zuunr.dcentb.rest.processor.mongo.MongoJsonDBCommandRunner;
import com.zuunr.dcentb.rest.processor.mongo.MongoJsonDBGetItemCommandCreator;
import com.zuunr.dcentb.rest.processor.mongo.NewStateToMongoItem;
import com.zuunr.json.JsonValue;

public class CUDItemRequestHandler extends RequestHandlerBase {

    private Processor[] processors;

    public CUDItemRequestHandler(JsonValue config) {
        super(config);

        processors = new Processor[]{
                config.as(ApiKeyAuthenticator.class),
                config.as(OASRequestDeserializer.class),
                config.as(UserInfoProvider.class),
                config.as(RequestAccessController.class),
                config.as(MongoJsonDBGetItemCommandCreator.class),  // create new state (get current state)
                config.as(MongoJsonDBCommandRunner.class),                           // create new state (get current state)
                config.as(VerifyMongoCommandExecution.class),                        // create new state (get current state -> item)
                config.as(SetMongoResultOrNullAsCurrentState.class),                 // state from mongo
                config.as(UpdateNewState.class),                    // current state + new state (from mongo or from apiModel)
                config.as(StateTransitionValidator.class),
                config.as(NewStateToMongoItem.class),               // new state -> mongo item
                config.as(MongoJsonDBCUDItemCommandCreator.class),   // mongoitem -> mongo command
                config.as(MongoJsonDBCommandRunner.class),                           // run mongo command
                config.as(VerifyMongoCommandExecution.class),
                config.as(ResponseFromNewState.class),                               // itemId={id} and newState={request.body}
        };
    }

    @Override
    public Processor[] getProcessors() {
        return processors;
    }
}
