package com.zuunr.dcentb.rest.requesthandler;

import com.zuunr.dcentb.rest.controller.RequestHandlerBase;
import com.zuunr.dcentb.rest.processor.*;
import com.zuunr.dcentb.rest.processor.accesscontrol.ApiKeyAuthenticator;
import com.zuunr.dcentb.rest.processor.accesscontrol.Authenticator;
import com.zuunr.dcentb.rest.processor.accesscontrol.RequestAccessController;
import com.zuunr.dcentb.rest.processor.accesscontrol.UserInfoProvider;
import com.zuunr.dcentb.rest.processor.mongo.DatabaseCommandRunner;
import com.zuunr.dcentb.rest.processor.mongo.DatabaseCommandReadCreator;
import com.zuunr.dcentb.rest.processor.MongoToRestItemTranslator;
import com.zuunr.json.JsonValue;

public class ReadItemRequestHandler extends RequestHandlerBase {

    private Processor[] processors;

    public ReadItemRequestHandler(JsonValue config) {
        super(config);
        Authenticator authenticator = config.as(Authenticator.class);
        ApiKeyAuthenticator apiKeyAuthenticator = config.as(ApiKeyAuthenticator.class);
        OASRequestDeserializer oasRequestDeserializer = config.as(OASRequestDeserializer.class);
        RequestAccessController requestAccessController = config.as(RequestAccessController.class);
        UserInfoProvider userInfoProvider = config.as(UserInfoProvider.class);

        DatabaseCommandReadCreator databaseCommandReadCreator = config.as(DatabaseCommandReadCreator.class);
        DatabaseCommandRunner databaseCommandRunner = config.as(DatabaseCommandRunner.class);
        MongoToRestItemTranslator mongoToRestItemTranslator = config.as(MongoToRestItemTranslator.class);

        processors = new Processor[] {
                apiKeyAuthenticator,
                oasRequestDeserializer,
                userInfoProvider,
                requestAccessController,
                databaseCommandReadCreator,
                databaseCommandRunner,
                mongoToRestItemTranslator
        };
    }

    @Override
    public Processor[] getProcessors() {
        return processors;
    }
}
