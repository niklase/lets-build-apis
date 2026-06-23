package com.zuunr.dcentb.rest.requesthandler;

import com.zuunr.dcentb.rest.controller.RequestHandlerBase;
import com.zuunr.dcentb.rest.processor.*;
import com.zuunr.dcentb.rest.processor.accesscontrol.ApiKeyAuthenticator;
import com.zuunr.dcentb.rest.processor.accesscontrol.RequestAccessController;
import com.zuunr.dcentb.rest.processor.accesscontrol.UserInfoProvider;
import com.zuunr.dcentb.rest.processor.apimodel.UpdateNewState;
import com.zuunr.dcentb.rest.processor.mongo.MongoJsonDBCommandRunner;
import com.zuunr.dcentb.rest.processor.mongo.NewStateToMongoItem;
import com.zuunr.json.JsonValue;

public class CreateItemRequestHandler extends RequestHandlerBase {

    private Processor[] processors;

    public CreateItemRequestHandler(JsonValue config) {
        super(config);
        ApiKeyAuthenticator apiKeyAuthenticator = config.as(ApiKeyAuthenticator.class);
        OASRequestDeserializer oasRequestDeserializer = config.as(OASRequestDeserializer.class);
        UserInfoProvider userInfoProvider = config.as(UserInfoProvider.class);
        RequestAccessController requestAccessController = config.as(RequestAccessController.class);
        UpdateNewState updateNewState = config.as(UpdateNewState.class);
        NewStateToMongoItem newStateToMongoItem = config.as(NewStateToMongoItem.class);
        MongoJsonDBCUDItemCommandCreator mongoJsonDBCUDItemCommandCreator = config.as(MongoJsonDBCUDItemCommandCreator.class);
        MongoJsonDBCommandRunner mongoJsonDBCommandRunner = config.as(MongoJsonDBCommandRunner.class);
        VerifyMongoCommandExecution verifyMongoCommandExecution = config.as(VerifyMongoCommandExecution.class);

        processors = new Processor[] {
                apiKeyAuthenticator,
                oasRequestDeserializer,
                userInfoProvider,
                requestAccessController,
                updateNewState,
                newStateToMongoItem,
                mongoJsonDBCUDItemCommandCreator,
                mongoJsonDBCommandRunner,
                verifyMongoCommandExecution};
    }

    @Override
    public Processor[] getProcessors() {
        return processors;
    }
}
