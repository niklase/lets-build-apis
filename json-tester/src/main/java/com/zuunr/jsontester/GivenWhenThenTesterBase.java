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
import java.io.InputStream;
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
    private static final String TEST_FILE_SCHEMA_RESOURCE = "given-when-then-test-file.schema.json";
    private static final JsonSchema TEST_FILE_SCHEMA = loadTestFileSchema();

    private static final long DEFAULT_INTERVAL_MILLIS = 200L;
    private static final long DEFAULT_TIMEOUT_MILLIS = 5000L;

    private static JsonSchema loadTestFileSchema() {
        try (InputStream is = GivenWhenThenTesterBase.class.getClassLoader().getResourceAsStream(TEST_FILE_SCHEMA_RESOURCE)) {
            if (is == null) {
                throw new IllegalStateException(TEST_FILE_SCHEMA_RESOURCE + " not found on classpath");
            }
            return JsonValueFactory.create(new String(is.readAllBytes())).as(JsonSchema.class);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load " + TEST_FILE_SCHEMA_RESOURCE, e);
        }
    }

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
        JsonArray sequence = testCase.get("sequence", JsonValue.NULL)
                .getJsonArray();

        if (sequence != null) {
            // Schema validation only targets this ("sequence"-array) shape - the older
            // single given/when/then fallback below predates it and isn't validated.
            boolean validateFormat = testJson.get(JsonArray.of("meta", "validateTestFileFormat"), JsonValue.TRUE).getBoolean();
            if (validateFormat) {
                validateTestFileFormat(testJson, jsonFileName);
            }
        } else if (testCase.get("tests") != null) {
            // "tests" was renamed to "sequence" - rejected outright rather than silently
            // falling through to the (unrelated) ancient given/when/then fallback below,
            // which would otherwise misinterpret this file in a far more confusing way.
            throw new RuntimeException("'tests' was renamed to 'sequence' - rename the top-level "
                    + "key in this file (no other change needed): " + jsonFileName);
        } else {
            // adding backwards compatibility
            sequence = JsonArray.of(
                    testCase,
                    testCase
                            .remove(JsonArray.of("given"))
                            .remove(JsonArray.of("then")),
                    testCase
                            .remove(JsonArray.of("given"))
                            .remove(JsonArray.of("when")));
        }

        JsonValue given = sequence.get(0).getJsonObject().get("given");

        if (given == null) {
            throw new RuntimeException("Missing 'given' in sequence: " + testCase);
        }

        JsonObject variables = testCase.get(JsonArray.of("meta", "variables"), JsonObject.EMPTY.jsonValue()).getJsonObject();

        given = updateWithVariableValues(given, variables);

        doGiven(given);
        variables = variables.putAll(additionalVariables());

        JsonObject globalDefaultRetry = testJson.get(JsonArray.of("meta", "defaultRetry"), JsonObject.EMPTY.jsonValue()).getJsonObject();

        for (int i = 1; i < sequence.size(); ) {

            JsonObject item = sequence.get(i).getJsonObject();

            if (item.containsKey("steps")) {

                // The explicit, self-contained group shape: exactly one "when", always
                // steps[0], followed by one or more "then"s - order guaranteed by array
                // position rather than inferred from adjacency. "description"/"meta"/"retry"
                // are siblings of "steps", not nested inside it.
                JsonArray steps = item.get("steps", JsonValue.NULL).getJsonArray();
                if (steps == null || steps.isEmpty()) {
                    throw new RuntimeException("Empty or missing 'steps' in sequence[" + i + "]");
                }

                JsonObject firstStep = steps.get(0).getJsonObject();
                JsonValue when = firstStep.get("when");
                if (when == null) {
                    throw new RuntimeException("'steps' must start with a 'when' in sequence[" + i + "]");
                }

                List<JsonObject> thenItems = new ArrayList<>();
                for (int s = 1; s < steps.size(); s++) {
                    JsonObject step = steps.get(s).getJsonObject();
                    if (step.get("when") != null) {
                        throw new RuntimeException("'when' may only appear once, as steps[0] - found again at steps[" + s + "] in sequence[" + i + "]");
                    }
                    if (step.get("then") == null) {
                        throw new RuntimeException("Every 'steps' element after the first must be a 'then' - steps[" + s + "] in sequence[" + i + "] is neither");
                    }
                    thenItems.add(step);
                }
                if (thenItems.isEmpty()) {
                    throw new RuntimeException("'steps' must contain at least one 'then' after the 'when' in sequence[" + i + "]");
                }

                String rawDescription = item.get("description", JsonValue.EMPTY_STRING).getString();
                JsonObject groupMeta = item.get("meta", JsonObject.EMPTY).getJsonObject();
                JsonObject retry = item.get("retry", JsonValue.NULL).getJsonObject();

                variables = runGroup(i, rawDescription, when, groupMeta, thenItems, retry, globalDefaultRetry, variables, testJson);
                i = i + 1;

            } else {

                // Legacy flat form, kept for backward compatibility: a "when" element
                // followed by one or more "then" blocks - every element up to (not
                // including) the next element that itself carries a "when" key.
                JsonValue when = item.get("when");
                if (when == null) {
                    throw new RuntimeException("Missing 'when' in sequence[" + i + "]");
                }
                if (item.get("retry") != null) {
                    throw new RuntimeException("'retry' is only valid inside a 'steps' group (wrap this in "
                            + "{\"description\": ..., \"retry\": ..., \"steps\": [...]}) - found directly on "
                            + "'when' in sequence[" + i + "]");
                }

                int groupEnd = i + 1;
                while (groupEnd < sequence.size()
                        && sequence.get(groupEnd).getJsonObject().get("when") == null
                        && !sequence.get(groupEnd).getJsonObject().containsKey("steps")) {
                    groupEnd++;
                }
                if (groupEnd == i + 1) {
                    throw new RuntimeException("Missing 'then' in sequence[" + (i + 1) + "]");
                }

                List<JsonObject> thenItems = new ArrayList<>();
                for (int k = i + 1; k < groupEnd; k++) {
                    JsonObject sequenceItem = sequence.get(k).getJsonObject();
                    if (sequenceItem.get("then") == null) {
                        throw new RuntimeException("Missing 'then' in sequence[" + k + "]");
                    }
                    thenItems.add(sequenceItem);
                }

                String rawDescription = item.get("description", JsonValue.EMPTY_STRING).getString();
                JsonObject whenMeta = item.get("meta", JsonObject.EMPTY).getJsonObject();

                variables = runGroup(i, rawDescription, when, whenMeta, thenItems, null, globalDefaultRetry, variables, testJson);
                i = groupEnd;
            }
        }
        LOGGER.info("Test ended: {}", jsonFileName);
    }

    /**
     * Validates the whole file against {@code given-when-then-test-file.schema.json} before a
     * single "when" runs - a malformed file (e.g. "retry" on a legacy flat "when", or "steps"
     * missing a "then") is rejected loudly up front rather than failing confusingly partway
     * through, or worse, being silently misinterpreted. Set {@code "meta": {"validateTestFileFormat":
     * false}} on a file to skip this - the one legitimate reason to is a fixture that
     * intentionally exercises a malformed shape to prove the harness itself rejects it (see
     * RetryConfigValidationTest), where schema validation would otherwise reject the file for an
     * unrelated reason and mask the specific runtime check actually being tested.
     */
    private void validateTestFileFormat(JsonValue testJson, Path jsonFileName) {
        JsonObject validationResult = SCHEMA_VALIDATOR.validate(testJson, TEST_FILE_SCHEMA, OutputStructure.DETAILED);
        if (!validationResult.get("valid", false).getBoolean()) {
            JsonValue apiError = ApiErrorCreator.ERROR_ARRAY_WITH_VIOLATIONS_ARRAY.createErrors(validationResult, testJson, TEST_FILE_SCHEMA);
            String errorReport = JsonObject.EMPTY.put("errors", apiError).asPrettyJson();
            LOGGER.error("{} does not match {}: {}", jsonFileName, TEST_FILE_SCHEMA_RESOURCE, errorReport);
            throw new RuntimeException(jsonFileName + " does not match " + TEST_FILE_SCHEMA_RESOURCE
                    + " (set \"meta\": {\"validateTestFileFormat\": false} to skip this check): " + errorReport);
        }
    }

    /**
     * Runs one "when" plus its "then"s - either shape (the legacy flat form or the explicit
     * "steps" form) funnels through here once it's been reduced to the same pieces: the
     * request, its group-level meta, and the ordered list of "then" items (each an object
     * carrying "then" and optionally "meta"). Retried via {@link #retryGroup} when {@code retry}
     * is non-null, executed once otherwise. Returns the variables map updated with anything
     * captured via "meta.setVariables" on any of this group's "then"s.
     */
    private JsonObject runGroup(int groupIndex, String rawDescription, JsonValue when, JsonObject groupMeta,
                                 List<JsonObject> thenItems, JsonObject retry, JsonObject globalDefaultRetry,
                                 JsonObject variables, JsonValue testJson) {

        String description = "sequence[" + groupIndex + "]: " + rawDescription;
        LOGGER.info("{}", description);

        JsonValue resolvedWhen = updateWithVariableValues(when, variables);

        // "then" bodies and the validation strategy chosen for each don't depend on how many
        // times "when" ends up being retried, so they're resolved once, up front.
        List<JsonValue> resolvedThens = new ArrayList<>();
        List<String> validationStrategies = new ArrayList<>();
        JsonArray metaValidationStrategy = JsonArray.of("meta", "validationStrategy");

        for (JsonObject thenItem : thenItems) {
            JsonValue then = thenItem.get("then");
            resolvedThens.add(updateWithVariableValues(then, variables));
            validationStrategies.add(thenItem.get(metaValidationStrategy, testJson.get(metaValidationStrategy, JsonValue.of("EXACT_MATCHING"))).getString());
        }

        JsonValue result = retry != null
                ? retryGroup(resolvedWhen, resolvedThens, validationStrategies, description, retry, globalDefaultRetry)
                : runOnce(resolvedWhen, resolvedThens, validationStrategies, description);

        for (int idx = 0; idx < thenItems.size(); idx++) {
            JsonObject thenItem = thenItems.get(idx);
            JsonValue then = resolvedThens.get(idx);

            JsonObject thenMeta = thenItem.get("meta", JsonObject.EMPTY).getJsonObject();
            onTestCase(rawDescription, resolvedWhen, groupMeta, then, thenMeta, result);

            JsonObject setVariablesConfig = thenMeta.get("setVariables", JsonObject.EMPTY).getJsonObject();
            variables = variables.putAll(setVariables(setVariablesConfig, result));
        }

        return variables;
    }

    private JsonValue runOnce(JsonValue when, List<JsonValue> resolvedThens, List<String> validationStrategies, String description) {
        JsonValue result = doWhen(when);
        validateGroup(result, resolvedThens, validationStrategies, description);
        return result;
    }

    /**
     * Repeats {@code doWhen(when)} - always immediately on the first attempt, no initial delay -
     * until every "then" in the group matches (an idempotent "when" is required - it is
     * re-executed on every attempt), or until "timeoutMillis" or "maxAttempts" - whichever is
     * reached FIRST - stops the loop. The attempt in flight at that point still goes through the
     * normal, throwing validation path, so exhausting the budget still produces an ordinary
     * assertion failure/diff rather than a separate "timed out" error shape. "maxAttempts" is an
     * independent safety cap, not scheduled to land near the timeout deadline - whichever limit
     * trips first wins, and which one is reported in the log line.
     * <p>
     * "retry.intervalMillis"/"timeoutMillis"/"maxAttempts" each independently fall back to
     * "meta.defaultRetry" on the test file, then to a hardcoded default (200ms interval, 5000ms
     * timeout, unlimited attempts).
     * <p>
     * "intervalMillis" may be a single number (the same wait before every retry) or an array -
     * an explicit backoff schedule: intervalMillis[0] is the wait before attempt 2,
     * intervalMillis[1] before attempt 3, and so on; once exhausted, its last value is reused
     * for every further wait until the loop stops - it never falls back to a formula.
     */
    private JsonValue retryGroup(JsonValue when, List<JsonValue> resolvedThens, List<String> validationStrategies, String description, JsonObject retry, JsonObject globalDefaultRetry) {

        List<Long> intervalSchedule = resolveIntervalSchedule(retry, globalDefaultRetry);
        long timeoutMillis = resolveTimeoutMillis(retry, globalDefaultRetry);
        Integer maxAttempts = resolveMaxAttempts(retry, globalDefaultRetry);
        long deadline = System.currentTimeMillis() + timeoutMillis;

        int attempt = 0;
        while (true) {
            attempt++;
            JsonValue result = doWhen(when);

            boolean timedOut = System.currentTimeMillis() >= deadline;
            boolean attemptsExhausted = maxAttempts != null && attempt >= maxAttempts;
            boolean isLastAttempt = timedOut || attemptsExhausted;

            try {
                validateGroup(result, resolvedThens, validationStrategies, description);
                LOGGER.info("{}: retry condition met after {} attempt(s)", description, attempt);
                return result;
            } catch (AssertionError notYetMatching) {
                if (isLastAttempt) {
                    String reason = attemptsExhausted && !timedOut
                            ? "maxAttempts (" + maxAttempts + ") reached"
                            : "timeoutMillis (" + timeoutMillis + " ms) elapsed";
                    LOGGER.info("{}: retry gave up after {} attempt(s) - {}", description, attempt, reason);
                    throw notYetMatching;
                }
                long sleepMillis = intervalSchedule.get(Math.min(attempt - 1, intervalSchedule.size() - 1));
                LOGGER.debug("{}: retry attempt {} not yet matching, retrying in {} ms", description, attempt, sleepMillis);
                try {
                    Thread.sleep(sleepMillis);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted while retrying: " + description, interrupted);
                }
            }
        }
    }

    private static List<Long> resolveIntervalSchedule(JsonObject retry, JsonObject globalDefaultRetry) {
        JsonValue interval = retry.get("intervalMillis");
        if (interval == null) {
            interval = globalDefaultRetry.get("intervalMillis");
        }
        if (interval == null) {
            return List.of(DEFAULT_INTERVAL_MILLIS);
        }
        if (interval.isJsonArray()) {
            JsonArray schedule = interval.getJsonArray();
            if (schedule.isEmpty()) {
                return List.of(DEFAULT_INTERVAL_MILLIS);
            }
            List<Long> millis = new ArrayList<>(schedule.size());
            for (int i = 0; i < schedule.size(); i++) {
                millis.add(schedule.get(i).getLong());
            }
            return millis;
        }
        return List.of(interval.getLong());
    }

    private static long resolveTimeoutMillis(JsonObject retry, JsonObject globalDefaultRetry) {
        JsonValue timeout = retry.get("timeoutMillis");
        if (timeout == null) {
            timeout = globalDefaultRetry.get("timeoutMillis");
        }
        return timeout == null ? DEFAULT_TIMEOUT_MILLIS : timeout.getLong();
    }

    private static Integer resolveMaxAttempts(JsonObject retry, JsonObject globalDefaultRetry) {
        JsonValue maxAttempts = retry.get("maxAttempts");
        if (maxAttempts == null) {
            maxAttempts = globalDefaultRetry.get("maxAttempts");
        }
        return maxAttempts == null ? null : maxAttempts.getInteger();
    }

    private void validateGroup(JsonValue result, List<JsonValue> resolvedThens, List<String> validationStrategies, String description) {
        for (int idx = 0; idx < resolvedThens.size(); idx++) {
            validateThen(resolvedThens.get(idx), result, validationStrategies.get(idx), description);
        }
    }

    /**
     * "ALLOWING_EXTRA_PROPERTIES" is also the one and only way to express a JSON-Schema-based
     * assertion - there is no separate "JSON_SCHEMA" validationStrategy. A "$schema" key
     * anywhere in "then" marks everything up to that point as a path into the result, and
     * its value as the schema to validate that path against - see the loop below. Putting
     * "$schema" at the very top of "then" (i.e. {@code "then": {"$schema": <schema>}}) makes
     * the path empty, which means "validate the whole result" - exactly what a dedicated
     * whole-result mode would do, with no separate mechanism needed.
     */
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
     * whenMeta is the group's own "meta" - the "meta" sibling of "when" on the sequence[i]
     * element for the legacy flat form, or the "meta" sibling of "steps"/"retry"/"description"
     * on the group wrapper for the "steps" form. thenMeta is the "meta" sibling of "then" for
     * whichever "then" this call is about (the same object that already carries
     * "setVariables"). Both default to an empty object when absent.
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
