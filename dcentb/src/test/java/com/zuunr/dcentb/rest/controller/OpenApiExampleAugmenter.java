package com.zuunr.dcentb.rest.controller;

import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Accumulates request/response pairs observed while running given-when-then
 * test files, and folds them into the matching operation of an OpenAPI
 * document as named examples under requestBody/responses "examples".
 *
 * The base document is the one referenced by a test file's tests[0].given.config.
 *
 * Instances are not shared across test classes; each owner (e.g. a test
 * class instance) should hold its own augmenter so that test files/classes
 * running in parallel don't contend on shared state. The instance methods
 * are synchronized only to guard against concurrent test-case invocations
 * against the *same* instance (e.g. parallel parameterized invocations of
 * one test class).
 */
public final class OpenApiExampleAugmenter {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenApiExampleAugmenter.class);
    private static final String MEDIA_TYPE = "application/json";

    private JsonObject document;
    private final Map<String, Set<String>> usedExampleKeys = new HashMap<>();

    public synchronized void initIfAbsent(JsonObject baseOpenApiDocument) {
        if (document == null) {
            document = baseOpenApiDocument;
        }
    }

    /**
     * @param requestDocExample whether the request body (if any, and only for a 2XX response) may become an example
     * @param responseDocExample whether the response body (if any) may become an example
     */
    public synchronized void record(String description, JsonObject when, boolean requestDocExample, JsonObject response, boolean responseDocExample) {
        if (document == null) {
            return;
        }

        String method = when.get("method", JsonValue.NULL).getString();
        String rawUri = when.get("uri", JsonValue.NULL).getString();
        if (method == null || rawUri == null) {
            return;
        }
        String path = rawUri.split("\\?", 2)[0];

        JsonObject paths = document.get("paths", JsonObject.EMPTY).getJsonObject();
        String template = matchPathTemplate(paths, path);
        if (template == null) {
            LOGGER.debug("No OpenAPI path template found for {} {}, skipping example capture", method, path);
            return;
        }

        String httpMethod = method.toLowerCase();
        int status = response.get("status", JsonValue.of(200)).getInteger();

        JsonValue requestBody = when.get("body");
        if (requestDocExample && requestBody != null && status >= 200 && status < 300) {
            String scope = template + " " + httpMethod + " #request";
            String exampleName = uniqueExampleKey(scope, description);
            document = document.put(
                    JsonArray.of("paths", template, httpMethod, "requestBody", "content", MEDIA_TYPE, "examples", exampleName),
                    JsonObject.EMPTY.put("summary", description).put("value", requestBody));
        }

        JsonValue responseBody = response.get("body");
        if (responseDocExample && responseBody != null) {
            JsonArray descriptionPointer = JsonArray.of("paths", template, httpMethod, "responses", String.valueOf(status), "description");
            if (document.get(descriptionPointer) == null) {
                document = document.put(descriptionPointer, "Observed during ControllerIT test run");
            }
            String scope = template + " " + httpMethod + " #response #" + status;
            String exampleName = uniqueExampleKey(scope, description);
            document = document.put(
                    JsonArray.of("paths", template, httpMethod, "responses", String.valueOf(status), "content", MEDIA_TYPE, "examples", exampleName),
                    JsonObject.EMPTY.put("summary", description).put("value", responseBody));
        }
    }

    private String uniqueExampleKey(String scope, String description) {
        Set<String> used = usedExampleKeys.computeIfAbsent(scope, k -> new HashSet<>());
        String baseSlug = slugify(description);
        String candidate = baseSlug;
        int suffix = 2;
        while (!used.add(candidate)) {
            candidate = baseSlug + "-" + suffix++;
        }
        return candidate;
    }

    private static String slugify(String text) {
        if (text == null || text.isBlank()) {
            return "example";
        }
        String slug = text.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (slug.length() > 60) {
            slug = slug.substring(0, 60).replaceAll("-+$", "");
        }
        return slug.isEmpty() ? "example" : slug;
    }

    private static String matchPathTemplate(JsonObject paths, String realPath) {
        String[] realSegments = realPath.split("/", -1);
        String bestMatch = null;
        int bestScore = Integer.MAX_VALUE;
        for (String template : paths.keySet()) {
            String[] templateSegments = template.split("/", -1);
            if (templateSegments.length != realSegments.length) {
                continue;
            }
            int score = 0;
            boolean matches = true;
            for (int i = 0; i < templateSegments.length; i++) {
                String segment = templateSegments[i];
                if (segment.startsWith("{") && segment.endsWith("}")) {
                    score++;
                } else if (!segment.equals(realSegments[i])) {
                    matches = false;
                    break;
                }
            }
            if (matches && score < bestScore) {
                bestScore = score;
                bestMatch = template;
            }
        }
        return bestMatch;
    }

    public synchronized void writeToFile(Path targetFile) throws IOException {
        if (document == null) {
            LOGGER.warn("No OpenAPI document was captured; skipping example-doc generation");
            return;
        }
        Files.createDirectories(targetFile.getParent());
        Files.writeString(targetFile, document.asPrettyJson());
        LOGGER.info("Generated OpenAPI document with recorded examples: {}", targetFile.toAbsolutePath());
    }
}
