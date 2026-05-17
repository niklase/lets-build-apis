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
import java.util.Iterator;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

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
                .map(Path::getFileName)
                ;
    }

    protected final void executeTest(Path jsonFileName) throws IOException {


        LOGGER.info("Test started: {}", jsonFileName);
        Class clazz = this.getClass();
        URL resource = clazz.getResource(clazz.getSimpleName() + "/" + jsonFileName.toString());

        Path testFilePath = Path.of(testFolderUri.getPath() + File.separatorChar + jsonFileName.toString());

        LOGGER.info("file {}", testFilePath);
        try {
            Path path = Paths.get(resource.toURI());
            LOGGER.info("Source file? {}", "file:///" + path.toString().replaceFirst("/target/test-classes/", "/src/test/resources/"));
        } catch (URISyntaxException e) {
            LOGGER.error("Cannot log source of test file.");
        }

        // Read JSON content (or parse, validate, etc.)
        String jsonContent = Files.readString(testFilePath);

        // Call the method you want to test, passing the JSON content
        // For example:

        JsonValue testJson = JsonValueFactory.create(jsonContent);
        JsonValue testCase = testJson;
        JsonArray tests = testCase.get("tests", JsonArray.of(testCase, testCase, testCase)).getJsonArray();

        Iterator<JsonValue> iterator = tests.iterator();

        while (iterator.hasNext()) {
            testCase = tests.head();
            tests = tests.tail();

            JsonValue next = iterator.next();

            JsonValue given;
            JsonValue when = null;
            JsonValue then = null;

            given = next.getJsonObject().get("given");
            if (given == null) {
                when = next.getJsonObject().get("when");
            }

            if (when == null) {
                next = iterator.hasNext() ? iterator.next() : JsonObject.EMPTY.jsonValue();
                when = next.get("when");
            }

            if (when == null) {
                then = next.get("then");
            }

            if (then == null) {
                next = iterator.hasNext() ? iterator.next() : null;
                then = next.get("then");
            }

            JsonValue result = doGivenWhen(given, when);

            String validationStrategy = testJson.get("meta", JsonObject.EMPTY).get("validationStrategy", "EXACT_MATCH").getString();
            switch (validationStrategy) {
                case "ALLOW_EXTRA_PROPERTIES": {
                    JsonObject thenToBeMerged = JsonObject.EMPTY.put(MERGE_ME, then);
                    JsonObject resultToBeMerged = JsonObject.EMPTY.put(MERGE_ME, result);

                    // JSON Merge Patch:  "then" patched by "actual result"
                    JsonObject thenMergedByResult = JSON_OBJECT_MERGER.merge(thenToBeMerged, resultToBeMerged);

                    // JSON Merge Patch: "actual result" patched by "then"
                    JsonObject resultMergedByThen = JSON_OBJECT_MERGER.merge(resultToBeMerged, thenToBeMerged);
                    assertEquals(thenMergedByResult.get(MERGE_ME), resultMergedByThen.get(MERGE_ME), "Present properties of 'then' mismatch");
                    assertEquals(resultMergedByThen.get(MERGE_ME), thenMergedByResult.get(MERGE_ME), "Present properties of 'then mismatch");
                    JsonArray propertiesOfThen = then.getPaths(true);
                    JsonArrayBuilder failures = JsonArray.EMPTY.builder();
                    for (int i = 0; i < propertiesOfThen.size(); i++){
                        JsonArray pathAndValue = propertiesOfThen.get(i).getJsonArray();
                        JsonPointer propertyPointer = pathAndValue.allButLast().as(JsonPointer.class);
                        JsonValue propertyValue = pathAndValue.last();
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
                        assertEquals(JsonObject.EMPTY.put("errors", JsonArray.EMPTY), apiError, "JSON Schema violated");
                    }
                    break;
                }
                default: {
                    assertEquals(then, result, "Exact match failed");
                }
            }
        }
        LOGGER.info("Test ended: {}", jsonFileName);
    }


    /**
     * Should return the value of given when
     *
     * @param given
     * @param when
     * @return
     */
    public abstract JsonValue doGivenWhen(JsonValue given, JsonValue when);


}
