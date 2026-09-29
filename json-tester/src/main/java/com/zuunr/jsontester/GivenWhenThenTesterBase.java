package com.zuunr.jsontester;

import com.zuunr.json.*;
import com.zuunr.json.pointer.JsonPointer;
import com.zuunr.json.schema.JsonSchema;
import com.zuunr.json.schema.validation.JsonSchemaValidator;
import com.zuunr.json.schema.validation.OutputStructure;
import com.zuunr.json.util.ApiErrorCreator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public abstract class GivenWhenThenTesterBase {

    private static final Logger LOGGER = LoggerFactory.getLogger(GivenWhenThenTesterBase.class);
    private static final JsonSchemaValidator SCHEMA_VALIDATOR = new JsonSchemaValidator();

    private static URI testFolderUri;

    protected static URI getTestFolder() {
        return testFolderUri;
    }

    protected static Stream<Path> testFiles(Class<? extends GivenWhenThenTesterBase> testClass) throws IOException, URISyntaxException {

        testFolderUri = testClass.getResource(testClass.getSimpleName()).toURI();
        return Files.list(Paths.get(testFolderUri))
                .filter(path -> path.toString().endsWith(".json"))
                .map(Path::getFileName);
    }


    protected final void executeTest(Path jsonFileName) throws IOException {

        LOGGER.info("Test started: {}", jsonFileName);
        Class clazz = this.getClass();
        URL resource = clazz.getResource(clazz.getSimpleName() + "/" + jsonFileName.toString());

        Path testFilePath = Path.of(testFolderUri.getPath() + File.separatorChar + jsonFileName.toString());

        LOGGER.debug("file: {}", testFilePath);
        try {
            Path path = Paths.get(resource.toURI());
            LOGGER.info("Source file(?): {}", "file:" + path.toString().replaceFirst("/target/test-classes/", "/src/test/resources/"));
        } catch (URISyntaxException e) {
            LOGGER.error("Cannot log source of test file.");
        }

        // Read JSON content (or parse, validate, etc.)
        String jsonContent = Files.readString(testFilePath);

        // Call the method you want to test, passing the JSON content
        // For example:

        JsonValue testJson = JsonValueFactory.create(jsonContent);
        JsonValue testCase = testJson;
        JsonArray tests = testCase.get("tests", JsonValue.NULL)
                .getJsonArray();

        if (tests == null) {
            // adding backwards compatibility
            tests = JsonArray.of(
                    testCase,
                    testCase
                            .remove(JsonArray.of("given"))
                            .remove(JsonArray.of("then")),
                    testCase
                            .remove(JsonArray.of("given"))
                            .remove(JsonArray.of("when")));
        }

        JsonValue given = tests.get(0).getJsonObject().get("given");

        if (given == null) {
            throw new RuntimeException("Missing 'given' in test case: " + testCase);
        }

        JsonObject variables = testCase.get(JsonArray.of("meta", "variables"), JsonObject.EMPTY.jsonValue()).getJsonObject();

        given = updateWithVariableValues(given, variables);

        doGiven(given);
        variables = variables.putAll(additionalVariables());

        JsonObject globalDefaultAwait = testJson.get(JsonArray.of("meta", "defaultAwait"), JsonObject.EMPTY.jsonValue()).getJsonObject();

        for (int i = 1; i < tests.size(); ) {

            JsonObject whenItem = tests.get(i).getJsonObject();
            String rawDescription = whenItem.get("description", JsonValue.EMPTY_STRING).getString();
            String description = "tests[" + i + "]: " + rawDescription;

            LOGGER.info("{}", description);
            JsonValue when = whenItem.get("when");
            if (when == null) {
                throw new RuntimeException("Missing 'when' in test element: " + i);
            }

            when = updateWithVariableValues(when, variables);

            // A "when" block is followed by one or more "then" blocks - every element up
            // to (not including) the next element that itself carries a "when" key.
            int groupEnd = i + 1;
            while (groupEnd < tests.size() && tests.get(groupEnd).getJsonObject().get("when") == null) {
                groupEnd++;
            }

            if (groupEnd == i + 1) {
                throw new RuntimeException("Missing 'then' in test element: " + (i + 1));
            }

            JsonObject whenMeta = whenItem.get("meta", JsonObject.EMPTY).getJsonObject();

            // "then" bodies and the validation strategy chosen for each don't depend on
            // how many times "when" ends up being retried, so they're resolved once, up front.
            List<JsonObject> testItems = new ArrayList<>();
            List<JsonValue> resolvedThens = new ArrayList<>();
            List<String> validationStrategies = new ArrayList<>();
            JsonArray metaValidationStrategy = JsonArray.of("meta", "validationStrategy");

            for (int k = i + 1; k < groupEnd; k++) {

                JsonObject testItem = tests.get(k).getJsonObject();
                JsonValue then = testItem.get("then");

                if (then == null) {
                    throw new RuntimeException("Missing 'then' in test element: " + k);
                }

                testItems.add(testItem);
                resolvedThens.add(updateWithVariableValues(then, variables));
                validationStrategies.add(testItem.get(metaValidationStrategy, testJson.get(metaValidationStrategy, JsonValue.of("EXACT_MATCHING"))).getString());
            }

            JsonValue result;
            JsonObject await = whenItem.get("await", JsonValue.NULL).getJsonObject();
            if (await != null) {
                result = awaitGroup(when, resolvedThens, validationStrategies, description, await, globalDefaultAwait);
            } else {
                result = doWhen(when);
                validateGroup(result, resolvedThens, validationStrategies, description);
            }

            for (int idx = 0; idx < testItems.size(); idx++) {
                JsonObject testItem = testItems.get(idx);
                JsonValue then = resolvedThens.get(idx);

                JsonObject thenMeta = testItem.get("meta", JsonObject.EMPTY).getJsonObject();
                onTestCase(rawDescription, when, whenMeta, then, thenMeta, result);

                JsonObject setVariables = testItem.get("meta", JsonObject.EMPTY).get("setVariables", JsonObject.EMPTY).getJsonObject();
                variables = variables.putAll(setVariables(setVariables, result));
            }

            i = groupEnd;
        }
        LOGGER.info("Test ended: {}", jsonFileName);
    }

    /**
     * Repeats {@code doWhen(when)} on an interval until every "then" in the group matches
     * (an idempotent "when" is required - it is re-executed on every attempt) or
     * {@code timeoutMillis} elapses. Retries are silent (logged at DEBUG); the attempt that
     * either finally matches or exhausts the time budget goes through the normal, throwing
     * validation path, so a real timeout still produces an ordinary assertion failure/diff
     * rather than a separate "timed out" error shape.
     * <p>
     * "await.intervalMillis"/"await.timeoutMillis" fall back to "meta.defaultAwait" on the
     * test file, then to 200ms/5000ms.
     */
    private JsonValue awaitGroup(JsonValue when, List<JsonValue> resolvedThens, List<String> validationStrategies, String description, JsonObject await, JsonObject globalDefaultAwait) {

        long intervalMillis = await.get("intervalMillis", globalDefaultAwait.get("intervalMillis", JsonValue.of(200))).getLong();
        long timeoutMillis = await.get("timeoutMillis", globalDefaultAwait.get("timeoutMillis", JsonValue.of(5000))).getLong();
        long deadline = System.currentTimeMillis() + timeoutMillis;

        int attempt = 0;
        while (true) {
            attempt++;
            JsonValue result = doWhen(when);
            boolean budgetExhausted = System.currentTimeMillis() >= deadline;

            if (budgetExhausted) {
                LOGGER.info("{}: await timed out after {} attempt(s) ({} ms budget) - asserting final result", description, attempt, timeoutMillis);
                validateGroup(result, resolvedThens, validationStrategies, description);
                return result;
            }

            try {
                validateGroup(result, resolvedThens, validationStrategies, description);
                LOGGER.info("{}: await condition met after {} attempt(s)", description, attempt);
                return result;
            } catch (AssertionError notYetMatching) {
                LOGGER.debug("{}: await attempt {} not yet matching, retrying in {} ms", description, attempt, intervalMillis);
                try {
                    Thread.sleep(intervalMillis);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted while awaiting condition: " + description, interrupted);
                }
            }
        }
    }

    private void validateGroup(JsonValue result, List<JsonValue> resolvedThens, List<String> validationStrategies, String description) {
        for (int idx = 0; idx < resolvedThens.size(); idx++) {
            validateThen(resolvedThens.get(idx), result, validationStrategies.get(idx), description);
        }
    }

    private void validateThen(JsonValue then, JsonValue result, String validationStrategy, String description) {
        switch (validationStrategy) {
            case "ALLOWING_EXTRA_PROPERTIES": {

                for (JsonValue pathJsonValue : then.getPaths(true)) {
                    JsonArray pathAndValue = pathJsonValue.getJsonArray();

                    int schemaIndex = pathAndValue.getIndexOfFirstMatch(jsonValue -> jsonValue.isString() && "$schema".equals(jsonValue.getString()));
                    if (schemaIndex != -1) {
                        JsonArray pathToValue = pathAndValue.subArray(0, schemaIndex);
                        JsonSchema schema = then.get(pathToValue.add("$schema")).as(JsonSchema.class);
                        JsonValue value = result.get(pathToValue);
                        JsonObject validationResult = SCHEMA_VALIDATOR.validate(value, schema, OutputStructure.DETAILED);
                        if (!validationResult.get("valid", false).getBoolean()) {
                            JsonValue apiError = ApiErrorCreator.ERROR_ARRAY_WITH_VIOLATIONS_ARRAY.createErrors(validationResult, value, schema);
                            LOGGER.error("JSON Schema error at {}: {}", pathToValue.addFirst("then").as(JsonPointer.class).getJsonPointerString().toString(), JsonObject.EMPTY.put("errors", apiError).asPrettyJson());
                            assertEquals(JsonObject.EMPTY.put("errors", JsonArray.EMPTY).jsonValue(), apiError, "JSON Schema violated");
                        }
                    } else {

                        JsonArray path = pathAndValue.allButLast();
                        JsonValue last = pathAndValue.last();

                        JsonValue actualValue = result.get(path);
                        if (last.isJsonObject() && actualValue != null && actualValue.isJsonObject()) {
                            // This then-leaf-object should not be validated as a leaf because it may contain more properties.
                        } else {
                            String pointer = path.as(JsonPointer.class).getJsonPointerString().toString();
                            assertEquals(pointer + ": " + last, pointer + ": " + actualValue, description);
                        }
                    }
                }
                break;
            }
            case "JSON_SCHEMA": {
                JsonObject validationResult = new JsonSchemaValidator().validate(result, then, OutputStructure.DETAILED);
                if (!validationResult.get("valid").getBoolean()) {
                    JsonValue apiError = ApiErrorCreator.ERROR_ARRAY_WITH_VIOLATIONS_ARRAY.createErrors(validationResult, result, then.as(JsonSchema.class));
                    LOGGER.error("JSON Schema error: {}", JsonObject.EMPTY.put("errors", apiError).asPrettyJson());
                    assertEquals(JsonObject.EMPTY.put("errors", JsonArray.EMPTY).jsonValue(), apiError, "JSON Schema violated");
                }
                break;
            }
            case "EXACT_MATCHING": {
                assertEquals(then, result, "Exact match failed");
                break;
            }
            default: {
                assertEquals(then, result, "Exact match failed");
            }
        }
    }

    private JsonValue updateWithVariableValues(JsonValue tobeUpdated, JsonObject variables) {
        JsonObject wrapper = JsonObject.EMPTY.put("_", tobeUpdated);
        for (JsonValue pathValue : wrapper.jsonValue().getPaths(true)) {
            JsonArray path = pathValue.getJsonArray();
            JsonValue last = path.last();
            if (last.isString()) {
                String lastString = last.getString();
                JsonValue varVal = variables.get(lastString);
                if (varVal != null) {
                    wrapper = wrapper.put(path.allButLast(), varVal);
                }
            }
        }
        return wrapper.get("_");
    }


    public abstract void doGiven(JsonValue given);

    /**
     * Optional hook allowing a subclass to contribute extra variables - e.g. computed
     * during doGiven - merged into (and taking precedence over) "meta.variables" from
     * the test file for the remainder of the test file's when/then blocks. Default
     * implementation contributes nothing.
     */
    protected JsonObject additionalVariables() {
        return JsonObject.EMPTY;
    }

    public abstract JsonValue doWhen(JsonValue when);

    /**
     * Optional hook invoked after a when/then pair has been executed and has passed
     * validation. Default implementation does nothing; override to observe the
     * description, request, expected result and actual result of each test case.
     * <p>
     * whenMeta is the "meta" object sibling of "when" (on the tests[i] element);
     * thenMeta is the "meta" object sibling of "then" (on the tests[i+1] element,
     * the same object that already carries "setVariables"). Both default to an
     * empty object when absent.
     */
    protected void onTestCase(String description, JsonValue when, JsonObject whenMeta, JsonValue then, JsonObject thenMeta, JsonValue result) {
        // no-op by default
    }


    private JsonObject setVariables(JsonObject setVariables, JsonValue result) {
        JsonObject variables = JsonObject.EMPTY;
        for (JsonValue key : setVariables.keys()) {
            String keyStr = key.getString();
            JsonPointer pointer = JsonValue.of(setVariables.get(keyStr).getString()).as(JsonPointer.class);

            JsonValue pointerValue = result.get(pointer);
            if (pointerValue == null) {
                throw new NullPointerException("No assignable value for " + keyStr + " at: " + pointer.getJsonPointerString().getString());
            }
            variables = variables.put(keyStr, result.get(pointer));
        }
        return variables;
    }
}
