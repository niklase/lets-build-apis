package com.zuunr.dcentb.rest.processor.accesscontrol;

import com.zuunr.dcentb.rest.processor.Processor;
import com.zuunr.dcentb.rest.processor.mongo.MongoJsonDBHandle;
import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import com.zuunr.mongodb.MongoJsonDB;

public class ApiKeyAuthenticator extends Processor {

    private final MongoJsonDB mongoJsonDB;
    private final String collection;

    public ApiKeyAuthenticator(JsonValue config) {
        super(config);
        this.mongoJsonDB = config.as(MongoJsonDBHandle.class).getMongoJsonDB();
        this.collection = config.getJsonObject()
                .get(X_DCENTB, JsonObject.EMPTY)
                .get("accessControl", JsonObject.EMPTY)
                .get("apiKeyCollection", "api-keys")
                .getString();
    }

    @Override
    public JsonObject process(JsonObject requestContext) {
        JsonObject request = requestContext.get(REQUEST).getJsonObject();
        String apiKey = request.get("headers", JsonObject.EMPTY).get("api-key", JsonArray.of("")).get(0).getString();

        if (apiKey.isEmpty()) {
            return requestContext.put(RESPONSE, JsonObject.EMPTY.put("status", 401).put("message", "Missing api-key"));
        }

        JsonObject findCommand = JsonObject.EMPTY.put("find", JsonObject.EMPTY
                .put("collection", collection)
                .put("filter", JsonObject.EMPTY
                        .put("_id", JsonArray.of(JsonObject.EMPTY.put("$eq", apiKey))))
                .put("limit", 1));

        JsonObject result = mongoJsonDB.runCommand(findCommand);
        JsonArray firstBatch = result.get("cursor", JsonObject.EMPTY).get("firstBatch", JsonArray.EMPTY).getJsonArray();

        if (firstBatch.isEmpty()) {
            return requestContext.put(RESPONSE, JsonObject.EMPTY.put("status", 401).put("message", "Invalid api-key"));
        }

        JsonObject authenticatedUser = firstBatch.get(0).getJsonObject().remove("_id");

        return requestContext.put("authenticatedUser", authenticatedUser);
    }
}
