package com.zuunr.example;

import com.zuunr.json.JsonObject;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

public class PathPatternPermissionConfig {

    private static final PathPatternParser PATH_PATTERN_PARSER = new PathPatternParser();

    public final JsonObject permissionConfig;
    public final PathPattern pathPattern;

    public PathPatternPermissionConfig (PathPattern pathPattern, JsonObject permissionConfig) {
        this.pathPattern = pathPattern;
        this.permissionConfig = permissionConfig;
    }

    public PathPatternPermissionConfig (String pathPattern, JsonObject permissionConfig) {
        this.pathPattern = PATH_PATTERN_PARSER.parse(pathPattern);
        this.permissionConfig = permissionConfig;
    }
}
