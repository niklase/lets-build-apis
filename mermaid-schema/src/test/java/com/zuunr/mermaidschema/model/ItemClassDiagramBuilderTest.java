package com.zuunr.mermaidschema.model;

import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValueFactory;
import com.zuunr.mermaidschema.ClassDiagramBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;


public class ItemClassDiagramBuilderTest {


    String json = """
                {
                  "properties": {
                    "cars": {
                      "type": "array",
                      "items": {
                        "$ref": "/$defs/Car"
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
            	}
            	Root --> "0..*" Car : cars
            	class Car {
            	  string brand
            	}
            """;


    @Test
    void test() {
        ClassDiagramBuilder classDiagramBuilder = new ClassDiagramBuilder();
        PatternTemplate[] patternTemplates =  {
                PatternTemplate.create("(.+)", "$1"),
                PatternTemplate.create("", "Root"),
        };
        ClassNamer classNamer = new ClassNamer(patternTemplates, JsonObject.EMPTY.put("$", "_").put("/", "_"));
        String result = classDiagramBuilder.create(JsonValueFactory.create(json), classNamer);
        System.out.print(result.replaceAll("\"", "").replaceAll(",","").replaceAll("[\\[]",""));
        assertEquals(expected, result);
    }

}
