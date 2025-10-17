package com.zuunr.mermaidschema.model;

import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValueFactory;
import com.zuunr.mermaidschema.ClassDiagramBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;


public class ClassDiagramBuilderTest {


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
                    "street": { "type": "string" },
                    "cars": {
                      "$ref": "/$defs/Car"
                    },
                    "residents" : {
                        "type": "object",
                        "properties": {
                            "number": {
                                "type": "integer"
                            }
                        }
                    }
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

    String expected = """
            classDiagram
            direction TB
            	class Root {
            	  integer age
            	  string name
            	}
            	Root --> "0..1" Root_address : address
            	Root --> "0..1" Car : car
            	class Root_address {
            	  string street
            	}
            	Root_address --> "0..1" Car : cars
            	Root_address --> "0..1" Root_address_residents : residents
            	class Car {
            	  string brand
            	}
            	class Root_address_residents {
            	  integer number
            	}
            """;


    @Test
    void test() {
        ClassDiagramBuilder classDiagramBuilder = new ClassDiagramBuilder();
        PatternTemplate[] patternTemplates = {
                PatternTemplate.create("(.+)", "$1"),
                PatternTemplate.create("", "Root"),
        };
        ClassNamer classNamer = new ClassNamer(patternTemplates, JsonObject.EMPTY.put("$", "_").put("/", "_"));
        String result = classDiagramBuilder.create(JsonValueFactory.create(json), classNamer);
        System.out.print(result.replaceAll("\"", "").replaceAll(",", "").replaceAll("[\\[]", ""));
        assertEquals(expected, result);
    }

}
