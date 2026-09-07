package com.zuunr.dcentb.rest.controller;

import com.zuunr.dcentb.rest.Response;
import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.mongodb.MongoJsonDB;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises SelfApiClient against the same local MongoDB ControllerIT uses, in its own
 * isolated api-key collection so it can't collide with demo data. Confirms a
 * by-reference self-call re-enters the exact same authentication/authorization
 * pipeline a real inbound HTTP request would - nothing is bypassed.
 */
class SelfApiClientTest {

    private static final String CONNECTION = "mongodb://admin:adminpassword@localhost:27017/?authSource=admin";
    private static final String DB = "dcentb-demo";

    private String apiKeyCollection;
    private MongoJsonDB mongoJsonDB;
    private SelfApiClient selfApiClient;

    @BeforeEach
    void setUp() {
        apiKeyCollection = "api-keys-selfapiclienttest-" + UUID.randomUUID();

        RequestHandlerProvider requestHandlerProvider = new RequestHandlerProvider(
                openApiDocument(apiKeyCollection),
                JsonObject.EMPTY.put("connection", CONNECTION).put("db", DB));
        Controller controller = new Controller(requestHandlerProvider);
        selfApiClient = new SelfApiClient(controller);

        mongoJsonDB = new MongoJsonDB(JsonObject.EMPTY.put("connection", CONNECTION).put("db", DB));
    }

    @AfterEach
    void tearDown() {
        mongoJsonDB.runCommand(JsonObject.EMPTY.put("drop", JsonObject.EMPTY.put("collection", apiKeyCollection)));
    }

    private static JsonObject openApiDocument(String apiKeyCollection) {
        JsonObject securitySchemes = JsonObject.EMPTY.put("ApiKeyAuth", JsonObject.EMPTY
                .put("type", "apiKey")
                .put("in", "header")
                .put("name", "api-key")
                .put("x-dcentb", JsonObject.EMPTY.put("apiKeyCollection", apiKeyCollection)));

        JsonObject getOperation = JsonObject.EMPTY
                .put("x-dcentb", JsonObject.EMPTY.put("accessControl", JsonObject.EMPTY
                        .put("permissionSchemas", JsonObject.EMPTY.put("admin", JsonObject.EMPTY
                                .put("requestSchema", JsonObject.EMPTY)
                                .put("responseSchema", JsonObject.EMPTY)))));

        return JsonObject.EMPTY
                .put("openapi", "3.1.0")
                .put("components", JsonObject.EMPTY.put("securitySchemes", securitySchemes))
                .put("paths", JsonObject.EMPTY.put("/widgets", JsonObject.EMPTY.put("get", getOperation)));
    }

    private void insertApiKey(String apiKey) {
        mongoJsonDB.runCommand(JsonObject.EMPTY.put("insert", JsonObject.EMPTY
                .put("collection", apiKeyCollection)
                .put("documents", JsonArray.of(JsonObject.EMPTY
                        .put("_id", apiKey)
                        .put("userId", "self-caller")
                        .put("permissions", JsonArray.of("admin"))))));
    }

    @Test
    void authenticatedSelfCallSucceeds() {
        String apiKey = UUID.randomUUID().toString();
        insertApiKey(apiKey);

        Response response = selfApiClient.call("GET", "/widgets",
                JsonObject.EMPTY.put("api-key", JsonArray.of(apiKey)), null);

        assertEquals(200, response.getStatus());
    }

    @Test
    void selfCallWithInvalidCredentialsIsRejectedLikeAnyExternalCall() {
        Response response = selfApiClient.call("GET", "/widgets",
                JsonObject.EMPTY.put("api-key", JsonArray.of("not-a-real-key")), null);

        assertEquals(401, response.getStatus());
    }
}
