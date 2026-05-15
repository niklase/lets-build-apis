package com.zuunr.dcentb.rest.requesthandler;

import com.zuunr.dcentb.rest.Request;
import com.zuunr.dcentb.rest.Response;
import com.zuunr.dcentb.rest.controller.RequestHandler;
import com.zuunr.dcentb.rest.processor.*;
import com.zuunr.dcentb.rest.processor.accesscontrol.*;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;

public class ReadCollectionRequestHandler implements RequestHandler {

    private ResponseAccessController responseAccessController;
    private Processor[] processors;

    public ReadCollectionRequestHandler(JsonValue config) {
        Authenticator authenticator = config.as(Authenticator.class);
        ApiKeyAuthenticator apiKeyAuthenticator = config.as(ApiKeyAuthenticator.class);
        OASRequestDeserializer oasRequestDeserializer = config.as(OASRequestDeserializer.class);
        RequestAccessController requestAccessController = config.as(RequestAccessController.class);
        UserInfoProvider userInfoProvider = config.as(UserInfoProvider.class);

        MongoJsonDBCommandCreator mongoJsonDBCommandCreator = config.as(MongoJsonDBCommandCreator.class);
        MongoJsonDBCommandRunner mongoJsonDBCommandRunner = config.as(MongoJsonDBCommandRunner.class);
        MongoToRestCollectionTranslator mongoToRestCollectionTranslator = config.as(MongoToRestCollectionTranslator.class);
        responseAccessController = config.as(ResponseAccessController.class);

        processors = new Processor[] {
                apiKeyAuthenticator,
                oasRequestDeserializer,
                userInfoProvider,
                requestAccessController,
                mongoJsonDBCommandCreator,
                mongoJsonDBCommandRunner,
                //new RequestContextDebugProcessor()
                mongoToRestCollectionTranslator
        };
    }

    public Response process(Request request) {

        JsonObject updatedRequestContext = JsonObject.EMPTY.put(Processor.REQUEST, request.asJsonObject());
        for (Processor processor : processors) {
            updatedRequestContext = processor.process(updatedRequestContext);
            JsonObject response = updatedRequestContext.get(Processor.RESPONSE, JsonValue.NULL).getJsonObject();
            if (response != null) {
                updatedRequestContext = responseAccessController.process(updatedRequestContext);
                response = updatedRequestContext.get(Processor.RESPONSE, JsonValue.NULL).getJsonObject();
                return response.as(Response.class);
            }
        }
        return JsonObject.EMPTY.put("status", 500).as(Response.class);
    }
}
