package com.zuunr.dcentb.rest.requesthandler;

import com.zuunr.dcentb.rest.controller.RequestHandlerBase;
import com.zuunr.dcentb.rest.processor.*;
import com.zuunr.dcentb.rest.processor.accesscontrol.*;
import com.zuunr.dcentb.rest.processor.mongo.DatabaseCommandRunner;
import com.zuunr.json.JsonValue;

public class ReadCollectionRequestHandler extends RequestHandlerBase {

    private Processor[] processors;

    public ReadCollectionRequestHandler(JsonValue config) {
        super(config);
        ApiKeyAuthenticator apiKeyAuthenticator = config.as(ApiKeyAuthenticator.class);
        OASRequestDeserializer oasRequestDeserializer = config.as(OASRequestDeserializer.class);
        RequestAccessController requestAccessController = config.as(RequestAccessController.class);
        UserInfoProvider userInfoProvider = config.as(UserInfoProvider.class);

        MongoJsonDBCommandCreator mongoJsonDBCommandCreator = config.as(MongoJsonDBCommandCreator.class);
        DatabaseCommandRunner databaseCommandRunner = config.as(DatabaseCommandRunner.class);
        MongoToRestCollectionTranslator mongoToRestCollectionTranslator = config.as(MongoToRestCollectionTranslator.class);

        processors = new Processor[] {
                apiKeyAuthenticator,
                oasRequestDeserializer,
                userInfoProvider,
                requestAccessController,
                config.as(PostGetCollectionBodyToQueryProcessor.class),
                mongoJsonDBCommandCreator,
                databaseCommandRunner,
                //new RequestContextDebugProcessor()
                mongoToRestCollectionTranslator
        };
    }

    @Override
    public Processor[] getProcessors() {
        return processors;
    }
}
