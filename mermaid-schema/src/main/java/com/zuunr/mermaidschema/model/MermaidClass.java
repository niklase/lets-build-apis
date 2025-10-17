package com.zuunr.mermaidschema.model;

import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonArrayBuilder;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import com.zuunr.json.pointer.JsonPointer;
import com.zuunr.json.schema.JsonSchema;
import com.zuunr.json.schema.Keywords;

public class MermaidClass {

    private JsonSchema jsonSchema;
    private JsonArray attributes;
    private JsonArray relations;
    private JsonArray nestedSchemas;

    public MermaidClass(JsonValue jsonValue) {
        jsonSchema = jsonValue.as(JsonSchema.class);
    }

    public void createAttributesAndRelations() {

        JsonArrayBuilder attrBuilder = JsonArray.EMPTY.builder();
        JsonArrayBuilder relBuilder = JsonArray.EMPTY.builder();

        JsonObject properties = jsonSchema.getProperties();
        if (properties == null) {
            JsonValue ref = jsonSchema.asJsonValue().get("$ref");
            if (ref != null) {
                relBuilder.add(JsonObject.EMPTY
                        .put("multiplicity", "0..1")
                        .put("jsonPointer", ref.as(JsonPointer.class).getJsonPointerString().getString()));
            }
            attributes = attrBuilder.build();
            relations = relBuilder.build();
            return;
        }
        JsonArray names = properties.keys().sort();

        for (int i = 0; i < names.size(); i++) {
            String propertyName = names.get(i).getString();
            JsonSchema subschema = properties.get(propertyName).as(JsonSchema.class);

            JsonValue ref = subschema.asJsonValue().get("$ref");
            if (ref != null) {
                relBuilder.add(JsonObject.EMPTY
                        .put("propertyName", propertyName)
                        .put("multiplicity", "0..1")
                        .put("jsonPointer", ref.as(JsonPointer.class).getJsonPointerString().getString()));
            } else {
                JsonValue type = subschema.getType();
                if (type.isString()) {
                    String typeString = type.getString();

                    switch (typeString) {
                        case Keywords.OBJECT: {
                            relBuilder.add(JsonObject.EMPTY
                                    .put("propertyName", propertyName)
                                    .put("multiplicity", "0..1")
                            );
                            break;
                        }
                        case Keywords.ARRAY: {
                            JsonObject relation = JsonObject.EMPTY
                                    .put("propertyName", propertyName)
                                    .put("multiplicity", "0..*");

                            JsonValue itemRef = subschema.get(JsonPointer.of("/items/$ref"), false);
                            if (itemRef != null) {
                                relation = relation.put("jsonPointer", itemRef.as(JsonPointer.class).getJsonPointerString().getString());
                            }

                            relBuilder.add(relation);
                            break;
                        }
                        default: {
                            attrBuilder.add(type.getString() + " " + propertyName);
                        }
                    }
                } else {
                    throw new RuntimeException("Only one type is supported so far.");
                }
            }
        }
        attributes = attrBuilder.build();
        relations = relBuilder.build();
    }

    public JsonArray getAttributes() {
        if (attributes == null) {
            createAttributesAndRelations();
        }
        return attributes;
    }

    public JsonArray getRelations() {
        if (relations == null) {
            createAttributesAndRelations();
        }
        return relations;
    }
}
