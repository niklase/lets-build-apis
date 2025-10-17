package com.zuunr.mermaidschema.model;

import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValueFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MermaidClassTest {

    @Test
    void testAttributes() {
        String json = """
                {
                  "type": "object",
                  "properties": {
                    "name": { "type": "string" },
                    "age": { "type": "integer" }
                  }
                }
                """;
        MermaidClass mermaidClass = JsonValueFactory.create(json).as(MermaidClass.class);
        assertEquals(JsonArray.of(
                "integer age",
                "string name"
        ), mermaidClass.getAttributes());
    }

    @Test
    void testRelations() {
        String json = """
                {
                  "type": "object",
                  "properties": {
                    "name": { "$ref": "#/$defs/FirstName" },
                    "age": { "$ref": "/$defs/Age" }
                  }
                }
                """;

        String expectedResult = """
                [
                  {
                    "propertyName": "age",
                    "jsonPointer": "/$defs/Age",
                    "multiplicity":"0..1"
                  },
                  {
                    "propertyName": "name",
                    "jsonPointer": "/$defs/FirstName",
                    "multiplicity":"0..1"
                  }
                ]
                """;

        MermaidClass mermaidClass = JsonValueFactory.create(json).as(MermaidClass.class);
        assertEquals(
                JsonValueFactory.create(expectedResult),
                mermaidClass.getRelations().jsonValue()
        );
    }


}
