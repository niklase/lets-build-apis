package com.zuunr.dcentb.rest.processor.mongo;

import com.zuunr.dcentb.rest.processor.Processor;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import com.zuunr.mongodb.MongoJsonDB;

public class MongoJsonDBHandle {

    private MongoJsonDB mongoJsonDB;

    public MongoJsonDBHandle(JsonValue openApiConfig) {
        JsonObject config = openApiConfig.getJsonObject().get(Processor.X_DCENTB, JsonObject.EMPTY).get("mongodb", JsonObject.EMPTY).getJsonObject();
        mongoJsonDB = new MongoJsonDB(config);
    }

    public MongoJsonDB getMongoJsonDB() {
        return mongoJsonDB;
    }
}
