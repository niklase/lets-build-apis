package com.zuunr.dcentb.rest.processor.accesscontrol;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.zuunr.dcentb.rest.processor.mongo.MongoJsonDBHandle;
import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import com.zuunr.mongodb.MongoJsonDB;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Exercises SharedSecretJwtAuthenticator against the same local MongoDB ControllerIT
 * uses (see students-test.json's dbSetup), in its own collection.
 */
class SharedSecretJwtAuthenticatorTest {

    private static final String CONNECTION = "mongodb://admin:adminpassword@localhost:27017/?authSource=admin";
    private static final String DB = "dcentb-demo";

    private String collection;
    private MongoJsonDB mongoJsonDB;
    private String secret;
    private SharedSecretJwtAuthenticator authenticator;

    @BeforeEach
    void setUp() {
        collection = "jwt-secrets-test-" + UUID.randomUUID();
        JsonValue config = configFor(collection);

        mongoJsonDB = config.as(MongoJsonDBHandle.class).getMongoJsonDB();
        secret = "test-secret-" + UUID.randomUUID();
        mongoJsonDB.runCommand(JsonObject.EMPTY.put("insert", JsonObject.EMPTY
                .put("collection", collection)
                .put("documents", JsonArray.of(JsonObject.EMPTY
                        .put("_id", SharedSecretJwtAuthenticator.SECRET_DOCUMENT_ID)
                        .put("secret", secret)))));

        authenticator = new SharedSecretJwtAuthenticator(config);
    }

    @AfterEach
    void tearDown() {
        mongoJsonDB.runCommand(JsonObject.EMPTY.put("drop", JsonObject.EMPTY.put("collection", collection)));
    }

    private static JsonValue configFor(String collection) {
        return JsonObject.EMPTY.put("x-dcentb", JsonObject.EMPTY
                .put("mongodb", JsonObject.EMPTY.put("connection", CONNECTION).put("db", DB))
                .put("accessControl", JsonObject.EMPTY.put("jwtSecretCollection", collection))
        ).jsonValue();
    }

    private JsonObject requestContextWithAuthorizationHeader(String headerValue) {
        JsonObject headers = headerValue == null
                ? JsonObject.EMPTY
                : JsonObject.EMPTY.put("authorization", JsonArray.of(headerValue));
        return JsonObject.EMPTY.put("request", JsonObject.EMPTY.put("headers", headers));
    }

    @Test
    void validTokenSignedWithSharedSecretIsAuthenticated() {
        String token = JWT.create().withClaim("userId", "demo-admin").sign(Algorithm.HMAC256(secret));

        JsonObject result = authenticator.process(requestContextWithAuthorizationHeader("Bearer " + token));

        assertNull(result.get("response"));
        assertEquals("demo-admin", result.get("authenticatedUser").getJsonObject().get("userId").getString());
    }

    @Test
    void tokenSignedWithWrongSecretIsRejected() {
        String token = JWT.create().withClaim("userId", "demo-admin").sign(Algorithm.HMAC256("wrong-secret"));

        JsonObject result = authenticator.process(requestContextWithAuthorizationHeader("Bearer " + token));

        assertEquals(401, result.get("response").getJsonObject().get("status").getInteger());
    }

    @Test
    void missingAuthorizationHeaderIsRejected() {
        JsonObject result = authenticator.process(requestContextWithAuthorizationHeader(null));

        assertEquals(401, result.get("response").getJsonObject().get("status").getInteger());
    }

    @Test
    void noSecretProvisionedYieldsUnauthorized() {
        SharedSecretJwtAuthenticator noSecretAuthenticator = new SharedSecretJwtAuthenticator(configFor(collection + "-empty"));

        String token = JWT.create().withClaim("userId", "demo-admin").sign(Algorithm.HMAC256("irrelevant"));
        JsonObject result = noSecretAuthenticator.process(requestContextWithAuthorizationHeader("Bearer " + token));

        assertEquals(401, result.get("response").getJsonObject().get("status").getInteger());
    }
}
