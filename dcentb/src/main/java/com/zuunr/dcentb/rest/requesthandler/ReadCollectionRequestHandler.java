package com.zuunr.dcentb.rest.requesthandler;

import com.zuunr.dcentb.rest.Request;
import com.zuunr.dcentb.rest.Response;
import com.zuunr.dcentb.rest.controller.RequestHandler;
import com.zuunr.dcentb.rest.controller.RequestHandlerBase;
import com.zuunr.dcentb.rest.processor.*;
import com.zuunr.dcentb.rest.processor.accesscontrol.*;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;

public class ReadCollectionRequestHandler extends RequestHandlerBase {

    private Processor[] processors;

    public ReadCollectionRequestHandler(JsonValue config) {
        super(config);
        Authenticator authenticator = config.as(Authenticator.class);
        ApiKeyAuthenticator apiKeyAuthenticator = config.as(ApiKeyAuthenticator.class);
        OASRequestDeserializer oasRequestDeserializer = config.as(OASRequestDeserializer.class);
        RequestAccessController requestAccessController = config.as(RequestAccessController.class);
        UserInfoProvider userInfoProvider = config.as(UserInfoProvider.class);

        MongoJsonDBCommandCreator mongoJsonDBCommandCreator = config.as(MongoJsonDBCommandCreator.class);
        MongoJsonDBCommandRunner mongoJsonDBCommandRunner = config.as(MongoJsonDBCommandRunner.class);
        MongoToRestCollectionTranslator mongoToRestCollectionTranslator = config.as(MongoToRestCollectionTranslator.class);

        processors = new Processor[] {
                apiKeyAuthenticator,
                oasRequestDeserializer,
                userInfoProvider,
                requestAccessController,
                new PostGetCollectionBodyToQueryProcessor(),
                mongoJsonDBCommandCreator,
                mongoJsonDBCommandRunner,
                //new RequestContextDebugProcessor()
                mongoToRestCollectionTranslator
        };
    }

    @Override
    public Processor[] getProcessors() {
        return processors;
    }
}
