package com.zuunr.dcentb.rest.processor.mongo;

import com.zuunr.dcentb.rest.processor.Processor;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import com.zuunr.mongodb.MongoJsonDB;

public class MongoJsonDBHandle {

    private MongoJsonDB mongoJsonDB;

    public MongoJsonDBHandle(JsonValue openApiConfig) {
        JsonObject mongoConfig = openApiConfig.getJsonObject().get(Processor.X_DCENTB, JsonObject.EMPTY).get("mongodb", JsonObject.EMPTY).getJsonObject();
        mongoJsonDB = mongoConfig.as(MongoJsonDB.class); // As mongoconfig is exactly the same object instance in all operations - MongoClient will be created only  once and be reused
    }

    public MongoJsonDB getMongoJsonDB() {
        return mongoJsonDB;
    }
}
