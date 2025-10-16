package com.zuunr.formbuilder;

import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;


public class DryagramRestMachineTest {


    @Test
    void test1() {
        RestMachine restMachine = new RestMachine(new InMemDB(), new TestItemStateUpdater());
        JsonObject response = restMachine.write(JsonObject.EMPTY
                .put("method", "POST")
                .put("uri", "/schema-diagrams")
                .put("body", JsonObject.EMPTY
                        .put("schema", JsonObject.EMPTY
                                .put("$defs", JsonObject.EMPTY))));

        assertEquals(201, response.get("status").getJsonNumber().intValue());
        assertEquals(true, response.get("headers").get("location").get(0).getString().matches("^.*/schema-diagrams/[^/?]+([?].*)?$"));
        assertNotNull(response.get("body").getJsonObject().get(JsonArray.of("meta", "id")).getString());

    }

    @Test
    void testX() {

        String requestSchemaString = """
                {
                    "properties": {
                        "request": {
                            "type": "object",
                            "properties": {
                                "method": {
                                    "type": "string"
                                },
                                "body": {
                                    "type": "object",
                                    "properties": {
                                        "schema": {
                                            "type": "object",
                                            "properties": {}
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                """;

    }
}
