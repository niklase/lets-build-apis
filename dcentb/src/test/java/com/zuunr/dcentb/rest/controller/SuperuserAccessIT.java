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

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies the core SUPERUSER mechanism (Processor.SUPERUSER_PERMISSION /
 * Processor.INTERNAL_PRINCIPAL / PreOperationAccessController / ResponseAccessController):
 * granted unconditionally and unfiltered when the request carries a top-level
 * "internalPrincipal" key, and - the actual security property - never granted by anything an
 * external HTTP request could ever send, including a header of the same name.
 */
class SuperuserAccessIT {

    private static final String CONNECTION = "mongodb://admin:adminpassword@localhost:27017/?authSource=admin";
    private static final String DB = "dcentb-demo";

    private RequestHandlerProvider requestHandlerProvider;

    @BeforeEach
    void setUp() throws Exception {
        MongoJsonDB mongoJsonDB = new MongoJsonDB(JsonObject.EMPTY.put("connection", CONNECTION).put("db", DB));
        mongoJsonDB.runCommand(JsonObject.EMPTY.put("drop", JsonObject.EMPTY.put("collection", "students")));
        mongoJsonDB.runCommand(JsonObject.EMPTY.put("drop", JsonObject.EMPTY.put("collection", "teachers")));
        mongoJsonDB.runCommand(JsonObject.EMPTY.put("insert", JsonObject.EMPTY
                .put("collection", "teachers")
                .put("documents", JsonArray.of(JsonObject.EMPTY
                        .put("_id", "teacher-X")
                        .put("teacherId", "teacher-X")
                        .put("name", "Teacher X")
                        .put("status", "ACTIVE")))));

        try (InputStream is = getClass().getClassLoader().getResourceAsStream("demo.openapi.json")) {
            JsonObject config = JsonValueFactory.create(new String(is.readAllBytes())).getJsonObject();
            requestHandlerProvider = new RequestHandlerProvider(config, JsonObject.EMPTY.put("connection", CONNECTION).put("db", DB));
        }
    }

    @Test
    void internalPrincipalGrantsUnfilteredAccessRegardlessOfCollectionConfig() {

        JsonObject createRequest = JsonObject.EMPTY
                .put("method", "POST")
                .put("uri", "/students")
                .put("headers", JsonObject.EMPTY.put("content-type", JsonArray.of("application/json")))
                .put(Processor.INTERNAL_PRINCIPAL, "SYSTEM")
                .put("body", JsonObject.EMPTY
                        .put("studentId", "student-superuser-test")
                        .put("teacherId", "teacher-X")
                        .put("name", "Superuser Test")
                        .put("email", "SUPER@example.com")
                        .put("attendancePercent", 100)
                        .put("grade", "C")
                        .asJson());

        Response<?> createResponse = execute(createRequest);
        assertEquals(201, createResponse.getStatus(), "SUPERUSER should create unrestricted by any role's writeItem schema: " + createResponse.asJsonObject());

        String id = createResponse.getBody().getJsonObject().get("meta").getJsonObject().get("id").getString();

        JsonObject readRequest = JsonObject.EMPTY
                .put("method", "GET")
                .put("uri", "/students/" + id)
                .put("headers", JsonObject.EMPTY)
                .put(Processor.INTERNAL_PRINCIPAL, "SYSTEM");

        Response<?> readResponse = execute(readRequest);
        assertEquals(200, readResponse.getStatus());

        JsonObject body = readResponse.getBody().getJsonObject();
        assertEquals("student-superuser-test", body.get("studentId").getString(), "Response should be fully unfiltered: " + body);
        assertEquals("teacher-X", body.get("teacherId").getString());
        assertEquals(100, body.get("attendancePercent").getInteger());
    }

    @Test
    void internalPrincipalCannotBeSpoofedViaAHeader() {

        JsonObject readCollectionRequest = JsonObject.EMPTY
                .put("method", "POST")
                .put("uri", "/students/getCollection")
                .put("headers", JsonObject.EMPTY.put("internalprincipal", JsonArray.of("SYSTEM")))
                .put("body", JsonObject.EMPTY);

        Response<?> response = execute(readCollectionRequest);
        assertEquals(401, response.getStatus(), "A header named internalPrincipal must not grant SUPERUSER - only the top-level request key (never reachable from real HTTP) may: " + response.asJsonObject());
    }

    private Response<?> execute(JsonObject request) {
        Request<Object> req = Request.of(request);
        return requestHandlerProvider.getRequestHandlerHandle(req).runRequestHandler(req);
    }
}
