package com.zuunr.dcentb.rest.requesthandler;

import com.zuunr.dcentb.rest.controller.RequestHandlerBase;
import com.zuunr.dcentb.rest.processor.*;
import com.zuunr.dcentb.rest.processor.accesscontrol.ApiKeyAuthenticator;
import com.zuunr.dcentb.rest.processor.accesscontrol.RequestAccessController;
import com.zuunr.dcentb.rest.processor.accesscontrol.UserInfoProvider;
import com.zuunr.dcentb.rest.processor.apimodel.UpdateNewState;
import com.zuunr.dcentb.rest.processor.mongo.NewStateToMongoItem;
import com.zuunr.dcentb.rest.processor.mongo.MongoJsonDBGetItemCommandCreator;
import com.zuunr.json.JsonValue;

public class CUDItemRequestHandler extends RequestHandlerBase {

    private Processor[] processors;

    public CUDItemRequestHandler(JsonValue config) {
        super(config);
        ApiKeyAuthenticator apiKeyAuthenticator = config.as(ApiKeyAuthenticator.class);
        OASRequestDeserializer oasRequestDeserializer = config.as(OASRequestDeserializer.class);
        UserInfoProvider userInfoProvider = config.as(UserInfoProvider.class);
        MongoJsonDBGetItemCommandCreator mongoJsonDBGetItemCommandCreator = config.as(MongoJsonDBGetItemCommandCreator.class);
        RequestAccessController requestAccessController = config.as(RequestAccessController.class);
        CreateNewStateFromRequest createNewStateFromRequest = config.as(CreateNewStateFromRequest.class);
        UpdateNewState updateNewState = config.as(UpdateNewState.class);
        SetMongoResultOrNullAsCurrentState setMongoResultOrNullAsCurrentState = config.as(SetMongoResultOrNullAsCurrentState.class);
        NewStateToMongoItem newStateToMongoItem = config.as(NewStateToMongoItem.class);
        MongoJsonDBInsertCommandCreator mongoJsonDBInsertCommandCreator = config.as(MongoJsonDBInsertCommandCreator.class);
        MongoJsonDBCommandRunner mongoJsonDBCommandRunner = config.as(MongoJsonDBCommandRunner.class);
        CreateMongoItemFromNewState createMongoItemFromNewState = config.as(CreateMongoItemFromNewState.class);
        VerifyMongoCommandExecution verifyMongoCommandExecution = config.as(VerifyMongoCommandExecution.class);
        ResponseFromNewState responseFromNewState = config.as(ResponseFromNewState.class);

        processors = new Processor[] {
                apiKeyAuthenticator,
                oasRequestDeserializer,
                userInfoProvider,
                requestAccessController,
                mongoJsonDBGetItemCommandCreator,  // create new state (get current state)
                mongoJsonDBCommandRunner,          // create new state (get current state)
                verifyMongoCommandExecution, // create new state (get current state -> item)
                setMongoResultOrNullAsCurrentState, // state from mongo
                updateNewState,                  // current state + new state (from mongo or from apiModel)
                newStateToMongoItem,             // new state -> mongo item
                mongoJsonDBInsertCommandCreator, // mongoitem -> mongo command
                mongoJsonDBCommandRunner,        // run mongo command
                verifyMongoCommandExecution,
                responseFromNewState,                 // itemId={id} and newState={request.body}
                };
    }

    @Override
    public Processor[] getProcessors() {
        return processors;
    }
}
