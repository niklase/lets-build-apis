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
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public abstract class GivenWhenThenTesterBase {

    private static final Logger LOGGER = LoggerFactory.getLogger(GivenWhenThenTesterBase.class);
    private static final String MERGE_ME = "mergeMe";

    private static final JsonObjectMerger JSON_OBJECT_MERGER = new JsonObjectMerger();
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

        for (int i = 1; i < tests.size(); i = i + 2) {

            LOGGER.info("tests[{}]: {}", i, tests.get(i).get("description", "<no desciption>").getString());
            JsonValue when = tests.get(i).get("when");
            if (when == null) {
                throw new RuntimeException("Missing 'when' in test element: " + i);
            }

            when = updateWithVariableValues(when, variables);

            JsonObject testItem = tests.get(i + 1 ).getJsonObject();
            JsonValue then = testItem.get("then");

            if (then == null) {
                throw new RuntimeException("Missing 'then' in test element: " + i + 1);
            }

            JsonValue result = doWhen(when);

            JsonArray metaValidationStrategy = JsonArray.of("meta", "validationStrategy");
            String validationStrategy = testItem.get(metaValidationStrategy, testJson.get(metaValidationStrategy, JsonValue.of("EXACT_MATCHING"))).getString();

            then = updateWithVariableValues(then, variables);

            switch (validationStrategy) {
                case "ALLOWING_EXTRA_PROPERTIES": {
                    JsonObject thenToBeMerged = JsonObject.EMPTY.put(MERGE_ME, then);
                    JsonObject resultToBeMerged = JsonObject.EMPTY.put(MERGE_ME, result);

                    // JSON Merge Patch:  "then" patched by "actual result"
                    JsonObject thenMergedByResult = JSON_OBJECT_MERGER.merge(thenToBeMerged, resultToBeMerged);

                    // JSON Merge Patch: "actual result" patched by "then"
                    JsonObject resultMergedByThen = JSON_OBJECT_MERGER.merge(resultToBeMerged, thenToBeMerged);
                    assertEquals(thenMergedByResult.get(MERGE_ME), resultMergedByThen.get(MERGE_ME), "Present properties of 'then' mismatch");
                    assertEquals(resultMergedByThen.get(MERGE_ME), thenMergedByResult.get(MERGE_ME), "Present properties of 'then' mismatch");
                    JsonArray propertiesOfThen = then.getPaths(false);
                    JsonArrayBuilder failures = JsonArray.EMPTY.builder();
                    for (int propIndex = 0; propIndex < propertiesOfThen.size(); propIndex++) {
                        JsonPointer propertyPointer = propertiesOfThen.get(propIndex).getJsonArray().as(JsonPointer.class);
                        JsonValue resultingValue = result.get(propertyPointer);
                        if (resultingValue == null) {
                            failures.add(propertyPointer.getJsonPointerString());
                        }
                    }
                    assertEquals("Mismatching pointers of 'then': " + JsonArray.EMPTY, "Mismatching pointers of 'then': " + failures.build(), "Present properties of 'then' mismatch:");
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
            JsonObject setVariables = testItem.get("meta", JsonObject.EMPTY).get("setVariables", JsonObject.EMPTY).getJsonObject();
            variables  = variables.putAll(setVariables(setVariables, result));
        }
        LOGGER.info("Test ended: {}", jsonFileName);
    }

    private JsonValue updateWithVariableValues(JsonValue tobeUpdated, JsonObject variables) {
        JsonObject wrapper = JsonObject.EMPTY.put("_", tobeUpdated);
        for (JsonValue pathValue: wrapper.jsonValue().getPaths(true)) {
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

    public abstract JsonValue doWhen(JsonValue when);


    private JsonObject setVariables(JsonObject setVariables, JsonValue result) {
        JsonObject variables = JsonObject.EMPTY;
        for (JsonValue key : setVariables.keys()) {
            String keyStr = key.getString();
            JsonPointer pointer = JsonValue.of(setVariables.get(keyStr).getString()).as(JsonPointer.class);
            variables = variables.put(keyStr, result.get(pointer));
        }
        return variables;
    }
}
