package com.zuunr.dcentb.rest.processor.accesscontrol;

import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.mongodb.MongoJsonDB;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises JwtSecretProvisioner against the same local MongoDB ControllerIT uses
 * (see students-test.json's dbSetup), in its own collection so it can't collide with
 * demo data.
 */
class JwtSecretProvisionerTest {

    private static final String CONNECTION = "mongodb://admin:adminpassword@localhost:27017/?authSource=admin";
    private static final String DB = "dcentb-demo";

    private String collection;
    private MongoJsonDB mongoJsonDB;

    @BeforeEach
    void setUp() {
        collection = "jwt-secrets-test-" + UUID.randomUUID();
        mongoJsonDB = new MongoJsonDB(JsonObject.EMPTY.put("connection", CONNECTION).put("db", DB));
    }

    @AfterEach
    void tearDown() {
        mongoJsonDB.runCommand(JsonObject.EMPTY.put("drop", JsonObject.EMPTY.put("collection", collection)));
    }

    private JsonObject openApiDocument(JsonArray jwtGeneration) {
        JsonObject xDcentb = JsonObject.EMPTY
                .put("mongodb", JsonObject.EMPTY.put("connection", CONNECTION).put("db", DB))
                .put("accessControl", JsonObject.EMPTY.put("jwtSecretCollection", collection));
        if (jwtGeneration != null) {
            xDcentb = xDcentb.put("jwtGeneration", jwtGeneration);
        }
        return JsonObject.EMPTY.put("x-dcentb", xDcentb);
    }

    private String storedSecret() {
        JsonObject find = JsonObject.EMPTY.put("find", JsonObject.EMPTY.put("collection", collection).put("limit", 1));
        JsonArray firstBatch = mongoJsonDB.runCommand(find).get("cursor").getJsonObject().get("firstBatch").getJsonArray();
        return firstBatch.isEmpty() ? null : firstBatch.get(0).getJsonObject().get("secret").getString();
    }

    @Test
    void createsSecretWhenAbsentAndReusesItOnSubsequentRuns() {
        JsonArray jwtGeneration = JsonArray.of(JsonObject.EMPTY.put("userId", "demo-admin").put("permissions", JsonArray.of("admin")));

        new JwtSecretProvisioner().provision(openApiDocument(jwtGeneration));
        String secretAfterFirstRun = storedSecret();
        assertNotNull(secretAfterFirstRun);

        new JwtSecretProvisioner().provision(openApiDocument(jwtGeneration));
        assertEquals(secretAfterFirstRun, storedSecret());
    }

    @Test
    void doesNothingWhenJwtGenerationIsAbsent() {
        new JwtSecretProvisioner().provision(openApiDocument(null));

        assertTrue(storedSecret() == null);
    }
}
