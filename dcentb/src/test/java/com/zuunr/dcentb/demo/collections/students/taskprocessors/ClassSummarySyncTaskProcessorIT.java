package com.zuunr.dcentb.demo.collections.students.taskprocessors;

import com.zuunr.dcentb.async.taskprocessing.TaskResult;
import com.zuunr.dcentb.rest.Request;
import com.zuunr.dcentb.rest.Response;
import com.zuunr.dcentb.rest.controller.Controller;
import com.zuunr.dcentb.rest.controller.RequestHandlerProvider;
import com.zuunr.dcentb.rest.controller.SystemApiClient;
import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValueFactory;
import com.zuunr.mongodb.MongoJsonDB;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Stage-1 test per docs/async-tasks-processing.md's testing strategy: deterministic, no real
 * change-stream listener or leader election involved. Real students/teachers are created
 * through the actual REST pipeline (proving ItemDecorator/teacher-embedding/referential
 * integrity work end to end for the "given" data), then a synthetic change-stream-shaped event
 * is built by hand and handed directly to ClassSummarySyncTaskProcessor.process(...) - "acting
 * as if the change stream event was received" - so the assertion is on the processor's own
 * logic, not on timing or a real leader-elected listener.
 */
class ClassSummarySyncTaskProcessorIT {

    private static final String CONNECTION = "mongodb://admin:adminpassword@localhost:27017/?authSource=admin";
    private static final String DB = "dcentb-demo";

    private RequestHandlerProvider requestHandlerProvider;
    private ClassSummarySyncTaskProcessor taskProcessor;
    private String teacherId;

    @BeforeEach
    void setUp() throws Exception {
        MongoJsonDB mongoJsonDB = new MongoJsonDB(JsonObject.EMPTY.put("connection", CONNECTION).put("db", DB));
        mongoJsonDB.runCommand(JsonObject.EMPTY.put("drop", JsonObject.EMPTY.put("collection", "students")));
        mongoJsonDB.runCommand(JsonObject.EMPTY.put("drop", JsonObject.EMPTY.put("collection", "teachers")));

        try (InputStream is = getClass().getClassLoader().getResourceAsStream("demo.openapi.json")) {
            JsonObject config = JsonValueFactory.create(new String(is.readAllBytes())).getJsonObject();
            requestHandlerProvider = new RequestHandlerProvider(config, JsonObject.EMPTY.put("connection", CONNECTION).put("db", DB));
        }

        Controller controller = new Controller(requestHandlerProvider);
        SystemApiClient systemApiClient = new SystemApiClient(controller);
        taskProcessor = new ClassSummarySyncTaskProcessor(systemApiClient);

        // Created through the real POST /teachers endpoint (not a raw Mongo insert) so it has
        // the exact meta shape a genuine create produces - a hand-built fixture missing
        // meta.href/etag/createdAt surfaced a real NPE in DatabaseCUDItemCommandCreator the
        // first time this test was written; going through the real endpoint avoids
        // hand-guessing that shape.
        Response<?> teacherResponse = executeAsSuperuser("POST", "/teachers", JsonObject.EMPTY
                .put("teacherId", "teacher-sync-test")
                .put("name", "Sync Test Teacher")
                .put("status", "ACTIVE"));
        assertEquals(201, teacherResponse.getStatus(), "setup failed: " + teacherResponse.asJsonObject());
        teacherId = teacherResponse.getBody().getJsonObject().get("meta").getJsonObject().get("id").getString();
    }

    @Test
    void recomputesTeachersClassSummaryFromCurrentStudents() {

        JsonObject anna = createStudent("student-anna-sync", "Anna", 90, "C");  // OK
        createStudent("student-bob-sync", "Bob", 40, "F");                     // AT_RISK
        createStudent("student-cara-sync", "Cara", 55, "C");                   // AT_RISK (>=60 threshold), grade rule satisfied (>=50 -> C allowed)

        JsonObject syntheticEvent = JsonObject.EMPTY
                .put("operationType", "insert")
                .put("fullDocument", anna.jsonValue());

        TaskResult result = taskProcessor.process(syntheticEvent);
        assertEquals(TaskResult.Status.SUCCESS, result.getStatus(), "unexpected: " + result);

        Response<?> teacherResponse = executeAsSuperuser("GET", "/teachers/" + teacherId, null);
        assertEquals(200, teacherResponse.getStatus());

        JsonObject classSummary = teacherResponse.getBody().getJsonObject().get("classSummary").getJsonObject();
        assertEquals(3, classSummary.get("studentCount").getInteger());
        assertEquals(2, classSummary.get("atRiskCount").getInteger());
        assertEquals("61.67", classSummary.get("averageAttendancePercent").getJsonNumber().toString());
    }

    @Test
    void isIdempotentUnderRedelivery() {

        JsonObject anna = createStudent("student-anna-idem", "Anna", 90, "C");

        JsonObject syntheticEvent = JsonObject.EMPTY
                .put("operationType", "insert")
                .put("fullDocument", anna.jsonValue());

        assertEquals(TaskResult.Status.SUCCESS, taskProcessor.process(syntheticEvent).getStatus());
        assertEquals(TaskResult.Status.SUCCESS, taskProcessor.process(syntheticEvent).getStatus());

        Response<?> teacherResponse = executeAsSuperuser("GET", "/teachers/" + teacherId, null);
        JsonObject classSummary = teacherResponse.getBody().getJsonObject().get("classSummary").getJsonObject();
        assertEquals(1, classSummary.get("studentCount").getInteger(), "redelivery must not double-count: " + classSummary);
    }

    private JsonObject createStudent(String studentId, String name, int attendancePercent, String grade) {
        JsonObject body = JsonObject.EMPTY
                .put("studentId", studentId)
                .put("teacherId", teacherId)
                .put("name", name)
                .put("email", name.toLowerCase() + "@example.com")
                .put("attendancePercent", attendancePercent)
                .put("grade", grade);

        Response<?> response = executeAsSuperuser("POST", "/students", body);
        assertEquals(201, response.getStatus(), "setup failed: " + response.asJsonObject());
        return response.getBody().getJsonObject();
    }

    private Response<?> executeAsSuperuser(String method, String uri, JsonObject body) {
        JsonObject headers = body == null
                ? JsonObject.EMPTY
                : JsonObject.EMPTY.put("content-type", JsonArray.of("application/json"));

        JsonObject request = JsonObject.EMPTY
                .put("method", method)
                .put("uri", uri)
                .put("headers", headers)
                .put(com.zuunr.dcentb.rest.processor.Processor.INTERNAL_PRINCIPAL, "SYSTEM");
        if (body != null) {
            request = request.put("body", body.asJson());
        }
        return run(request);
    }

    private Response<?> run(JsonObject request) {
        Request<Object> req = Request.of(request);
        return requestHandlerProvider.getRequestHandlerHandle(req).runRequestHandler(req);
    }
}
