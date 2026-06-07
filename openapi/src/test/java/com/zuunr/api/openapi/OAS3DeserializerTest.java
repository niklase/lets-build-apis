package com.zuunr.api.openapi;

import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import com.zuunr.jsontester.GivenWhenThenTesterBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.util.stream.Stream;


public class OAS3DeserializerTest extends GivenWhenThenTesterBase {

    private JsonObject givenOpenApiDefAndPathToOperation;

    /*
     * This method implementation may be copied as-is to any other subclass of GivenWhenThenBaseTester
     */
    static Stream<Path> testFiles() throws Exception {
        return testFiles((Class<? extends GivenWhenThenTesterBase>) new Object() {
        }.getClass().getEnclosingClass()); // NOSONAR
    }

    /*
     * This method implementation and annotations may be copied as-is to any other subclass of GivenWhenThenBaseTester
     */
    @DisplayName("Run test for each JSON file")
    @ParameterizedTest(name = "{index} => JSON file: {0}")
    @MethodSource("testFiles")
    void test(Path testsFolderPath) throws Exception {
        executeTest(testsFolderPath);
    }

    @Override
    public void doGiven(JsonValue given) {
        givenOpenApiDefAndPathToOperation = given.getJsonObject();
    }

    @Override
    public JsonValue doWhen(JsonValue whenExchangeWithRequest) {

        JsonObject result = OAS3Deserializer.deserializeRequest(
                whenExchangeWithRequest.getJsonObject(),
                givenOpenApiDefAndPathToOperation.get("openApiDef").getJsonObject(),
                givenOpenApiDefAndPathToOperation.get("pathToOperation").getJsonArray());

        return result.jsonValue();
    }
}
