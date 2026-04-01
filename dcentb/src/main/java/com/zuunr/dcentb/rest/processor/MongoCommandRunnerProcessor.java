package com.zuunr.dcentb.rest.processor;

import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import com.zuunr.mongodb.MongoJsonDB;

public class MongoCommandRunnerProcessor implements Processor {

    private static final String COMMAND = "command";
    private JsonArray pathsToTemplatesInOperand = JsonArray.of(JsonArray.of(COMMAND));
    private JsonArray templates;

    private String connection;
    private String db;
    private MongoJsonDB mongoDB;


    public MongoCommandRunnerProcessor(JsonValue config) {
        JsonObject mongodbConfig = config.get(Processor.X_DCENTB, JsonObject.EMPTY).get("mongodb", JsonObject.EMPTY).getJsonObject();
        mongoDB = new MongoJsonDB(mongodbConfig);
    }

    @Override
    public JsonObject process(JsonObject requestContext) {
        return mongoDB.runCommand(requestContext.get(REQUEST).getJsonObject());
    }

}
