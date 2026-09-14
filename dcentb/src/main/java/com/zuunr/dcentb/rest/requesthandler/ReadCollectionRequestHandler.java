package com.zuunr.dcentb.rest.requesthandler;

import com.zuunr.dcentb.rest.controller.RequestHandlerBase;
import com.zuunr.dcentb.rest.processor.*;
import com.zuunr.dcentb.rest.processor.accesscontrol.*;
import com.zuunr.dcentb.rest.processor.apimodel.CurrentStateItemDecorator;
import com.zuunr.dcentb.rest.processor.mongo.DatabaseCommandReadCreator;
import com.zuunr.dcentb.rest.processor.mongo.DatabaseCommandRunner;
import com.zuunr.json.JsonValue;

public class ReadCollectionRequestHandler extends RequestHandlerBase {

    private Processor[] processors;

    public ReadCollectionRequestHandler(JsonValue config) {
        super(config);
        
        processors = new Processor[] {
                config.as(AuthenticationProcessor.class),
                config.as(OASRequestDeserializer.class),
                config.as(UserInfoProvider.class),
                config.as(RequestAccessController.class),
                config.as(PostGetCollectionBodyToQueryProcessor.class),
                config.as(MongoJsonDBCommandCreator.class),             // This one could be part of the DatabaseCommandCreator
                config.as(DatabaseCommandRunner.class),                 // create new state (get current state)
                config.as(DatabaseCommandResponseVerifier.class),       // create new state (get current state -> item)
                config.as(MongoToRestCollectionTranslator.class)
        };
    }

    @Override
    public Processor[] getProcessors() {
        return processors;
    }
}
