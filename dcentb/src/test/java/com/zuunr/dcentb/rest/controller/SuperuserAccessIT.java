package com.zuunr.dcentb.rest.controller;

import com.zuunr.dcentb.rest.Request;
import com.zuunr.dcentb.rest.Response;
import com.zuunr.dcentb.rest.processor.Processor;
import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValueFactory;
import com.zuunr.mongodb.MongoJsonDB;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies the core SUPERUSER mechanism (Processor.SUPERUSER_PERMISSION /
 * Processor.INTERNAL_PRINCIPAL / PreOperationAccessController / ResponseAccessController):
 * granted unconditionally and unfiltered when the request carries a top-level
 * "internalPrincipal" key, and - the actual security property - never granted by anything an
 * external HTTP request could ever send, including a header of the same name.
 * <p>
 * Deliberately against a minimal, synthetic "widgets" collection rather than any real demo API -
 * this is a core framework guarantee, not something that should depend on a particular app's
 * OpenAPI document existing on the classpath.
 */
class SuperuserAccessIT {

    private static final String CONNECTION = "mongodb://admin:adminpassword@localhost:27017/?authSource=admin";
    private static final String DB = "dcentb-test";

    private RequestHandlerProvider requestHandlerProvider;

    @BeforeEach
    void setUp() {
        MongoJsonDB mongoJsonDB = new MongoJsonDB(JsonObject.EMPTY.put("connection", CONNECTION).put("db", DB));
        mongoJsonDB.runCommand(JsonObject.EMPTY.put("drop", JsonObject.EMPTY.put("collection", "widgets")));

        // "nobody-real-has-this" is a permission no api-key in this test ever has - a real
        // caller can never pass PreOperationAccessController/ResponseAccessController here.
        // SUPERUSER must still succeed, fully unfiltered, regardless: it short-circuits both
        // controllers before this (or any) permissionSchemas entry is ever consulted.
        JsonObject config = JsonValueFactory.create("""
                {
                  "openapi": "3.1.0",
                  "components": {
                    "securitySchemes": {
                      "ApiKeyAuth": { "type": "apiKey", "in": "header", "name": "api-key" }
                    }
                  },
                  "paths": {
                    "/widgets": {
                      "post": {
                        "requestBody": { "content": { "application/json": { "schema": {} } } },
                        "x-dcentb": { "accessControl": { "permissionSchemas": {
                          "nobody-real-has-this": { "requestSchema": {}, "responseSchema": {} }
                        }}}
                      }
                    },
                    "/widgets/{id}": {
                      "get": {
                        "x-dcentb": { "accessControl": { "permissionSchemas": {
                          "nobody-real-has-this": { "requestSchema": {}, "responseSchema": {} }
                        }}}
                      }
                    }
                  }
                }
                """).getJsonObject();

        requestHandlerProvider = new RequestHandlerProvider(config, JsonObject.EMPTY.put("connection", CONNECTION).put("db", DB));
    }

    @Test
    void internalPrincipalGrantsUnfilteredAccessRegardlessOfCollectionConfig() {

        JsonObject createRequest = JsonObject.EMPTY
                .put("method", "POST")
                .put("uri", "/widgets")
                .put("headers", JsonObject.EMPTY.put("content-type", JsonArray.of("application/json")))
                .put(Processor.INTERNAL_PRINCIPAL, "SYSTEM")
                .put("body", JsonObject.EMPTY
                        .put("name", "Superuser Test Widget")
                        .put("secret", "only-superuser-should-see-this")
                        .asJson());

        Response<?> createResponse = execute(createRequest);
        assertEquals(201, createResponse.getStatus(), "SUPERUSER should create unrestricted by any role's writeItem schema: " + createResponse.asJsonObject());

        String id = createResponse.getBody().getJsonObject().get("meta").getJsonObject().get("id").getString();

        JsonObject readRequest = JsonObject.EMPTY
                .put("method", "GET")
                .put("uri", "/widgets/" + id)
                .put("headers", JsonObject.EMPTY)
                .put(Processor.INTERNAL_PRINCIPAL, "SYSTEM");

        Response<?> readResponse = execute(readRequest);
        assertEquals(200, readResponse.getStatus());

        JsonObject body = readResponse.getBody().getJsonObject();
        assertEquals("Superuser Test Widget", body.get("name").getString(), "Response should be fully unfiltered: " + body);
        assertEquals("only-superuser-should-see-this", body.get("secret").getString());
    }

    @Test
    void internalPrincipalCannotBeSpoofedViaAHeader() {

        JsonObject createRequest = JsonObject.EMPTY
                .put("method", "POST")
                .put("uri", "/widgets")
                .put("headers", JsonObject.EMPTY.put("internalprincipal", JsonArray.of("SYSTEM")))
                .put("body", JsonObject.EMPTY);

        Response<?> response = execute(createRequest);
        assertEquals(401, response.getStatus(), "A header named internalPrincipal must not grant SUPERUSER - only the top-level request key (never reachable from real HTTP) may: " + response.asJsonObject());
    }

    private Response<?> execute(JsonObject request) {
        Request<Object> req = Request.of(request);
        return requestHandlerProvider.getRequestHandlerHandle(req).runRequestHandler(req);
    }
}
