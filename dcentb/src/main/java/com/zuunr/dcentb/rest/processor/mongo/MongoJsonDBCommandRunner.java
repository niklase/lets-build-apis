package com.zuunr.dcentb.rest.processor.mongo;

import com.zuunr.dcentb.rest.processor.Processor;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import com.zuunr.mongodb.MongoJsonDB;

public class MongoJsonDBCommandRunner implements Processor {

    private MongoJsonDB mongoDB;

    public MongoJsonDBCommandRunner(JsonValue config) {
        JsonObject mongodbConfig = config.get("operation", JsonObject.EMPTY).get(Processor.X_DCENTB, JsonObject.EMPTY).get("mongodb", JsonObject.EMPTY).getJsonObject();
        mongoDB = config.as(MongoJsonDBHandle.class).getMongoJsonDB();
    }

    @Override
    public JsonObject process(JsonObject requestContext) {

        JsonObject mongoCommand = requestContext.get("mongoCommand", JsonValue.NULL).getJsonObject();
        if (mongoCommand != null) {
            requestContext = requestContext.put("mongoResult", mongoDB.runCommand(mongoCommand));
        }
        return requestContext;
    }
}
