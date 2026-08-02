package com.zuunr.dcentb.rest.processor.mongo;

import com.zuunr.dcentb.rest.controller.RequestHandlerConfig;
import com.zuunr.dcentb.rest.processor.Processor;
import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;

public class MongoJsonDBGetItemCommandCreator extends Processor {

    RequestHandlerConfig requestHandlerConfig;

    public MongoJsonDBGetItemCommandCreator(JsonValue jsonValue) {
        this(jsonValue.as(RequestHandlerConfig.class));
    }

    private MongoJsonDBGetItemCommandCreator(RequestHandlerConfig requestHandlerConfig) {
        this.requestHandlerConfig = requestHandlerConfig;
    }

    @Override
    public JsonObject process(JsonObject requestContext) {


        String id = requestContext.get("request", JsonObject.EMPTY).get("pathParameters", JsonObject.EMPTY).get("id", JsonValue.NULL).getString();

        if (id != null) {

            String collection = requestHandlerConfig.getOperationConfig().getXDcentb().getMongodb().getCollection();

            JsonObject mongoCommand = JsonObject.EMPTY
                    .put("find", JsonObject.EMPTY
                            .put("collection", collection)
                            .put("filter", JsonObject.EMPTY
                                    .put("_id", JsonArray.of(
                                            JsonObject.EMPTY.put("$eq",
                                                    requestContext.get("request").get("pathParameters").get("id"))))));


            requestContext = requestContext.put("mongoCommand", mongoCommand);
        }
        return requestContext;
    }


}
