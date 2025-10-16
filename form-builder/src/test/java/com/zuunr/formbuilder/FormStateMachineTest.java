package com.zuunr.formbuilder;

import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class FormStateMachineTest {

    @Test
    void test() {
        RestMachine formStateMachine = new RestMachine();
        JsonObject requestBody = JsonObject.EMPTY.put("status", "TODO");
        JsonObject response = formStateMachine.write(JsonObject.EMPTY
                .put("method", "POST")
                .put("uri", "/tasks")
                .put("body", requestBody));

        JsonObject responseBody = response.get("body").getJsonObject();
        assertEquals(requestBody, responseBody.remove("meta"));
        assertNotNull(responseBody.get(JsonArray.of("meta", "id")));

        JsonObject responseOfNonExisting = formStateMachine.write(JsonObject.EMPTY
                .put("method", "PATCH")
                .put("uri", "/tasks/123")
                .put("body", JsonObject.EMPTY
                        .put("status", "TODO")));
        assertEquals(404, responseOfNonExisting.get("status").getJsonNumber().intValue());

        String location = response.get("headers").get("location").get(0).getString();
        JsonObject responseOfUpdate = formStateMachine.write(JsonObject.EMPTY
                .put("method", "PATCH")
                .put("uri", location)
                .put("body", JsonObject.EMPTY
                        .put("status", "DO_THIS_LATER")));
        assertEquals(409, responseOfUpdate.get("status").getJsonNumber().intValue());

        JsonObject responseOfValidUpdate = formStateMachine.write(JsonObject.EMPTY
                .put("method", "PATCH")
                .put("uri", location)
                .put("body", JsonObject.EMPTY
                        .put("status", "DOING")));
        assertEquals(200, responseOfValidUpdate.get("status").getJsonNumber().intValue());

        JsonObject responseOfGet = formStateMachine.read(JsonObject.EMPTY
                .put("method", "GET")
                .put("uri", location));
        assertEquals(200, responseOfGet.get("status").getJsonNumber().intValue());
        assertEquals("DOING", responseOfGet.get("body").getJsonObject().get("status").getString());

        JsonObject responseOfValidDelete = formStateMachine.write(JsonObject.EMPTY
                .put("method", "DELETE")
                .put("uri", location)
        );
        assertEquals(204, responseOfValidDelete.get("status").getJsonNumber().intValue());

        JsonObject responseOfUpdateAfterDelete = formStateMachine.write(JsonObject.EMPTY
                .put("method", "PATCH")
                .put("uri", location)
                .put("body", JsonObject.EMPTY
                        .put("status", "DO_THIS_LATER")));
        assertEquals(404, responseOfUpdateAfterDelete.get("status").getJsonNumber().intValue());
    }
}