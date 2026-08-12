package com.zuunr.dcentb.rest.processor.accesscontrol;

import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonArrayBuilder;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import com.zuunr.mongodb.MongoJsonDB;

import java.util.UUID;

public class ApiKeyProvisioner {

    public void provision(JsonObject openApiDocument) {
        JsonValue apiKeyGenerationValue = openApiDocument.get(JsonArray.of("x-dcentb", "apiKeyGeneration"));
        if (apiKeyGenerationValue == null) {
            return;
        }
        JsonArray entries = apiKeyGenerationValue.getJsonArray();
        if (entries == null || entries.isEmpty()) {
            return;
        }

        JsonObject xDcentb = openApiDocument.get("x-dcentb", JsonObject.EMPTY).getJsonObject();
        JsonObject mongoConfig = xDcentb.get("mongodb", JsonObject.EMPTY).getJsonObject();
        MongoJsonDB mongoJsonDB = mongoConfig.as(MongoJsonDB.class);
        String collection = xDcentb.get("accessControl", JsonObject.EMPTY).get("apiKeyCollection", "api-keys").getString();

        for (int i = 0; i < entries.size(); i++) {
            provisionEntry(entries.get(i).getJsonObject(), mongoJsonDB, collection);
        }
    }

    private void provisionEntry(JsonObject entry, MongoJsonDB mongoJsonDB, String collection) {
        String userId = entry.get("userId").getString();

        JsonArrayBuilder andConditions = JsonArray.EMPTY.builder();
        JsonArray keys = entry.keys();
        JsonArray values = entry.values();
        for (int i = 0; i < keys.size(); i++) {
            andConditions.add(JsonObject.EMPTY.put(
                    keys.get(i).getString(),
                    JsonArray.of(JsonObject.EMPTY.put("$eq", values.get(i)))));
        }

        JsonObject findCommand = JsonObject.EMPTY.put("find", JsonObject.EMPTY
                .put("collection", collection)
                .put("filter", JsonObject.EMPTY.put("$and", andConditions.build()))
                .put("limit", 1));

        JsonObject result = mongoJsonDB.runCommand(findCommand);
        JsonArray firstBatch = result.get("cursor", JsonObject.EMPTY).get("firstBatch", JsonArray.EMPTY).getJsonArray();

        if (firstBatch.isEmpty() || !firstBatch.get(0).getJsonObject().remove("_id").equals(entry.remove("_id"))) {
            String apiKey = UUID.randomUUID().toString();
            JsonObject document = entry.put("_id", apiKey);
            mongoJsonDB.runCommand(JsonObject.EMPTY.put("insert", JsonObject.EMPTY
                    .put("collection", collection)
                    .put("documents", JsonArray.of(document))));
            System.out.println("[dcentb] api-key generated  userId=" + userId + "  key=" + apiKey + "  document=" + document);
        } else {
            System.out.println("[dcentb] api-key exists     userId=" + userId + " (key in database collection 'api-keys' as property '_id')");
        }
    }
}
