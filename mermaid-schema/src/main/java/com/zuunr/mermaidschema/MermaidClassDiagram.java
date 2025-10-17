package com.zuunr.mermaidschema;

import com.zuunr.json.JsonValue;
import com.zuunr.mermaidschema.model.ClassNamer;

public class MermaidClassDiagram {

    private static final  ClassDiagramBuilder CLASS_DIAGRAM_BUILDER = new ClassDiagramBuilder();

    private String diagram;

    public MermaidClassDiagram(JsonValue jsonSchema){
        diagram = CLASS_DIAGRAM_BUILDER.create(jsonSchema);
    }

    public String toString(){
        return diagram;
    }
}
