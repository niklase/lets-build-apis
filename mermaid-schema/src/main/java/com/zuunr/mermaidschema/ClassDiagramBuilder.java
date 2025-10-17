package com.zuunr.mermaidschema;

import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonArrayBuilder;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import com.zuunr.mermaidschema.model.ClassNamer;
import com.zuunr.mermaidschema.model.JsonSchemaFlattener;
import com.zuunr.mermaidschema.model.MermaidClass;

public class ClassDiagramBuilder {

    private ClassNamer classNamer;

    public ClassDiagramBuilder() {
        classNamer = new ClassNamer(null, null);
    }

    public String create(JsonValue schema) {
        return create(schema, classNamer);
    }

    public String create(JsonValue schema, ClassNamer classNamer) {

        JsonArrayBuilder diagramRows = JsonArray.EMPTY.builder();

        JsonObject flattened = JsonSchemaFlattener.flatten(JsonArray.EMPTY, schema);
        JsonArray keys = flattened.keys();
        JsonArray values = flattened.values();
        for (int i = 0; i < keys.size(); i++) {

            String className = keys.get(i).getString();
            className = classNamer.nameOf(className);

            createClass(className, values.get(i), diagramRows);
            addRelations(className, values.get(i), diagramRows, classNamer);
        }
        JsonArray diagram = diagramRows.build();

        StringBuilder stringBuilder = new StringBuilder("classDiagram\n");
        stringBuilder.append("direction TB\n");
        diagram.stream().forEachOrdered(row -> {
            stringBuilder.append("\t").append(row.getString()).append("\n");
        });

        return stringBuilder.toString();
    }

    void createClass(String className, JsonValue schema, JsonArrayBuilder diagramRows) {
        diagramRows.add("class " + className + " {");
        addAttributes(schema, diagramRows);
        diagramRows.add("}");

    }

    void addAttributes(JsonValue schema, JsonArrayBuilder diagramRows) {
        for (JsonValue relation : schema.as(MermaidClass.class).getAttributes()) {
            diagramRows.add("  " + relation.getString());
        }
    }

    void addRelations(String fromClass, JsonValue schema, JsonArrayBuilder diagramRows, ClassNamer classNamer) {
        for (JsonValue relation : schema.as(MermaidClass.class).getRelations()) {

            String jsonPointer = relation.get("jsonPointer", JsonValue.NULL).getString();
            if (jsonPointer == null) {
                String propertyName = relation.get("propertyName", JsonValue.NULL).getString();
                if (propertyName == null) {
                    jsonPointer = fromClass + "_item";
                } else {
                    jsonPointer = fromClass + "_" + propertyName;
                }
            }

            JsonValue multiplicity = relation.get("multiplicity");

            String propertyName = relation.get("propertyName", JsonValue.NULL).getString();
            if (propertyName != null) {
                diagramRows.add(fromClass + " --> " + multiplicity.asJson() + " " + classNamer.nameOf(jsonPointer) + " : " + relation.get("propertyName").getString());
            }
        }
    }
}
