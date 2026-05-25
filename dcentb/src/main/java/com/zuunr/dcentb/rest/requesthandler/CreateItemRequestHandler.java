package com.zuunr.dcentb.rest.requesthandler;

import com.zuunr.dcentb.rest.controller.RequestHandlerBase;
import com.zuunr.dcentb.rest.processor.*;
import com.zuunr.dcentb.rest.processor.accesscontrol.ApiKeyAuthenticator;
import com.zuunr.dcentb.rest.processor.accesscontrol.RequestAccessController;
import com.zuunr.dcentb.rest.processor.accesscontrol.UserInfoProvider;
import com.zuunr.json.JsonValue;

public class CreateItemRequestHandler extends RequestHandlerBase {

    private Processor[] processors;

    public CreateItemRequestHandler(JsonValue config) {
        super(config);
        ApiKeyAuthenticator apiKeyAuthenticator = config.as(ApiKeyAuthenticator.class);
        OASRequestDeserializer oasRequestDeserializer = config.as(OASRequestDeserializer.class);
        UserInfoProvider userInfoProvider = config.as(UserInfoProvider.class);
        RequestAccessController requestAccessController = config.as(RequestAccessController.class);
        MongoJsonDBInsertCommandCreator mongoJsonDBInsertCommandCreator = config.as(MongoJsonDBInsertCommandCreator.class);
        MongoJsonDBCommandRunner mongoJsonDBCommandRunner = config.as(MongoJsonDBCommandRunner.class);
        MongoItemToRestResponseTranslator mongoItemToRestResponseTranslator = config.as(MongoItemToRestResponseTranslator.class);

        processors = new Processor[] {
                apiKeyAuthenticator,
                oasRequestDeserializer,
                userInfoProvider,
                requestAccessController,
                mongoJsonDBInsertCommandCreator,
                mongoJsonDBCommandRunner,
                mongoItemToRestResponseTranslator};
    }

    @Override
    public Processor[] getProcessors() {
        return processors;
    }
}
