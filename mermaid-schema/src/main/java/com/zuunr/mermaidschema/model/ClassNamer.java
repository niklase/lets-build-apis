package com.zuunr.mermaidschema.model;

import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;

import java.util.regex.Matcher;

public class ClassNamer {
    private final PatternTemplate[] patternTemplates;
    private final JsonObject escapeChars;


    public ClassNamer(PatternTemplate[] patternTemplates, JsonObject escapeChars) {
        this.patternTemplates = patternTemplates;
        this.escapeChars = escapeChars;
    }

    public String nameOf(String jsonPointer) {


        return jsonPointer.replaceAll("^$", "Root").replaceAll("^/[$]defs/", "").replaceAll("^/", "Root_").replaceAll("/", "_");
        /*
        JsonObject stringReplacement = JsonArray.EMPTY.builder().add(
                JsonArray.of("")




        for (PatternTemplate patternTemplate : patternTemplates) {
            Matcher matcher = patternTemplate.pattern.matcher(jsonPointer);
            if (matcher.find()) {
                String unescaped =  matcher.replaceAll(patternTemplate.template);
                return escapeChars(unescaped);
            }
        }
        throw new RuntimeException("No pattern matches json pointer: "+jsonPointer);

        */
    }

    private String escapeChars(String input) {
        StringBuilder sb = new StringBuilder();
        for (char c : input.toCharArray()) {
            String s = String.valueOf(c);
            sb.append(escapeChars.get(s, s).getString());
        }
        return sb.toString();
    }
}


