package com.zuunr.dcentb.rest.controller;

import com.zuunr.dcentb.rest.Request;
import com.zuunr.dcentb.rest.requesthandler.DcentbGivenWhenThenTester;
import com.zuunr.dcentb.rest.util.BackendTime;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import com.zuunr.jsontester.GivenWhenThenTesterBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ControllerIT extends DcentbGivenWhenThenTester {


    static {
        BackendTime.setNow(1785420000000L); // 2026-07-30 14:00:00
    }

    private final OpenApiExampleAugmenter exampleAugmenter = new OpenApiExampleAugmenter();

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
    void  test(Path testsFolderPath) throws Exception {
        executeTest(testsFolderPath);
    }

    RequestHandlerProvider requestHandlerProvider;

    @Override
    public void doGiven(JsonValue given) {
        super.doGiven(given);
        exampleAugmenter.initIfAbsent(rawConfig);
        requestHandlerProvider = new RequestHandlerProvider(config);
    }

    @AfterAll
    void writeOpenApiDocumentWithExamples() throws IOException {
        exampleAugmenter.writeToFile(Paths.get("target", "generated-openapi", "demo.openapi.with-examples.json"));
    }

    @Override
    public JsonValue doWhen(JsonValue given) {

        if (given.get("body", JsonValue.NULL).isJsonObject()) {
            given = given.getJsonObject().put("body", given.get("body").asJson()).jsonValue();
        }
        Request<Object> request = Request.of(given.getJsonObject());
        return requestHandlerProvider
                .getRequestHandlerHandle(request).runRequestHandler(request)
                .asJsonObject()
                .jsonValue();
    }

    @Override
    protected void onTestCase(String description, JsonValue when, JsonObject whenMeta, JsonValue then, JsonObject thenMeta, JsonValue result) {
        boolean requestDocExample = whenMeta.get("docExample", JsonValue.of(true)).getBoolean();
        boolean responseDocExample = thenMeta.get("docExample", JsonValue.of(true)).getBoolean();
        exampleAugmenter.record(description, when.getJsonObject(), requestDocExample, result.getJsonObject(), responseDocExample);
    }

}
