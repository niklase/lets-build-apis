package com.zuunr.mermaidschema.model;

import com.zuunr.json.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;


public class JsonSchemaFlattenerTest {


    String json = """
                
                {
                  "properties": {
                    "car": { 
                      "$ref": "/$defs/Car" 
                    },
                    "name": { "type": "string" },
                    "age": { "type": "integer" },
                    "address": {
                      "type": "object",
                      "properties": {
                        "street": { "type": "string" }
                      }
                    }
                  },
                  "$defs": {
                    "Car": {
                      "type": "object",
                      "properties": {
                        "brand": { "type": "string"}
                      }
                    }
                  }
                }
                
                """;


    @Test
    void testFlatten() {


        String expectedOutput = """
                
                {
                  "": {
                    "properties": {
                      "age": {
                        "type": "integer"
                      },
                      "name": {
                        "type": "string"
                      },
                      "address": {
                        "properties": {
                          "street": {
                            "type": "string"
                          }
                        },
                        "type": "object"
                      },
                      "car": {
                        "$ref": "/$defs/Car"
                      }
                    },
                    "$defs": {
                      "Car": {
                        "properties": {
                          "brand": {
                            "type": "string"
                          }
                        },
                        "type": "object"
                      }
                    }
                  },
                  "/address": {
                    "properties": {
                      "street": {
                        "type": "string"
                      }
                    },
                    "type": "object"
                  },
                  "/$defs/Car": {
                    "properties": {
                      "brand": {
                        "type": "string"
                      }
                    },
                    "type": "object"
                  }
                }
                
                """;

        JsonObject flattened = JsonSchemaFlattener.flatten(JsonArray.EMPTY, JsonValueFactory.create(json));
        assertEquals(JsonValueFactory.create(expectedOutput), flattened.jsonValue());
    }


}
