package com.zuunr.example;

import com.zuunr.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.Arrays;
import java.util.List;

public class BestMatchFinderWithPreprocessingTest {

    private static final PathPatternParser parser = new PathPatternParser();

    @Test
    void test() {
        // Original list of unsorted patterns
        List<PathPatternPermissionConfig> patterns = Arrays.asList(
                new PathPatternPermissionConfig("/customer/{name}/contact/{id}", JsonObject.EMPTY.put("permission", "A")),
                new PathPatternPermissionConfig("/customer/john/{y}/123", JsonObject.EMPTY.put("permission", "G")),
                new PathPatternPermissionConfig("/customer/john/{x}/123", JsonObject.EMPTY.put("permission", "D")),
                new PathPatternPermissionConfig("/customer/olle/contact/4556", JsonObject.EMPTY.put("permission", "C")),
                new PathPatternPermissionConfig("/{role}/john/contact/123", JsonObject.EMPTY.put("permission", "B"))
        );

        // Pre-process patterns: compile them using PathPatternParser
        //List<PathPattern> compiledPatterns = preprocessPatterns(patterns);

        // The request URI to match
        String requestUri = "/customer/john/contact/123";

        // Find the best match
        PathPatternPermissionConfig bestMatch = findBestMatch(patterns, requestUri);

        if (bestMatch != null) {
            System.out.println("Best matching pattern: " + bestMatch.permissionConfig);
        } else {
            System.out.println("No matching pattern found.");
        }
    }

    // Find the best match
    public static PathPatternPermissionConfig findBestMatch(List<PathPatternPermissionConfig> pathPatternPermissionConfigs, String requestUri) {
        PathPatternPermissionConfig bestMatch = null;

        PathContainer pathContainer = PathContainer.parsePath(requestUri);
        for (PathPatternPermissionConfig pathPatternPermissionConfig : pathPatternPermissionConfigs) {
            if (pathPatternPermissionConfig.pathPattern.matches(pathContainer)) {

                if (bestMatch == null) {
                    bestMatch = pathPatternPermissionConfig;
                } else {
                    int comparision = pathPatternPermissionConfig.pathPattern.compareTo(bestMatch.pathPattern);
                    if (comparision == 0) {
                        throw new RuntimeException("These paths matches the same request URIs: " + pathPatternPermissionConfig.pathPattern.toString() + ", " + bestMatch.pathPattern);
                    }
                    if (comparision < 1) {
                        bestMatch = pathPatternPermissionConfig;
                    }
                }
            }
        }
        return bestMatch;
    }
}