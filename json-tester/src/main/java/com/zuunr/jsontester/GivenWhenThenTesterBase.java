package com.zuunr.jsontester;

import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonObjectMerger;
import com.zuunr.json.JsonValue;
import com.zuunr.json.JsonValueFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
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
                .filter(path -> path.toString().endsWith(".json")).map(Path::getFileName);
    }

    protected final void executeTest(Path jsonFileName) throws IOException {
        // Read JSON content (or parse, validate, etc.)
        Path testFilePath = Path.of(testFolderUri.getPath() + File.separatorChar + jsonFileName.toString());
        LOGGER.info("file {}", testFilePath);
        String jsonContent = Files.readString(testFilePath);

        // Call the method you want to test, passing the JSON content
        // For example:

        JsonValue testCase = JsonValueFactory.create(jsonContent);

        JsonValue result = doGivenWhen(testCase.get("given"), testCase.get("when"));
        JsonValue then = testCase.get("then");

        if (Boolean.TRUE == testCase.get("meta", JsonObject.EMPTY).get("additionalPropertiesAllowed", false).getBoolean()) {
            JsonObject thenToBeMerged = JsonObject.EMPTY.put(MERGE_ME, then);
            JsonObject resultToBeMerged = JsonObject.EMPTY.put(MERGE_ME, result);

            // JSON Merge Patch:  "then" patched by "actual result"
            JsonObject thenMergedByResult = JSON_OBJECT_MERGER.merge(thenToBeMerged, resultToBeMerged);

            // JSON Merge Patch: "actual result" patched by "then"
            JsonObject resultMergedByThen = JSON_OBJECT_MERGER.merge(resultToBeMerged, thenToBeMerged);

            assertEquals(thenMergedByResult.get(MERGE_ME), resultMergedByThen.get(MERGE_ME));
        } else {
            assertEquals(then, result);
        }
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
