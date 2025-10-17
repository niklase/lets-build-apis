package com.zuunr.mermaidschema.model;

import com.zuunr.json.JsonObject;

import java.util.regex.Pattern;

public class PatternTemplate {
    public final Pattern pattern;
    public final String template;

    private PatternTemplate(String pattern, String template) {
        this.pattern = Pattern.compile(pattern);
        this.template = template;
    }

    public static PatternTemplate create(String pattern, String template) {
        return new PatternTemplate(pattern, template);
    }


}

