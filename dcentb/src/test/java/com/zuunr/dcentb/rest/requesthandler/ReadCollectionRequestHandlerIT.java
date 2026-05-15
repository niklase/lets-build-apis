package com.zuunr.dcentb.rest.requesthandler;

import com.zuunr.dcentb.rest.Request;
import com.zuunr.dcentb.rest.controller.RequestHandlerProvider;
import com.zuunr.json.JsonValue;
import com.zuunr.jsontester.GivenWhenThenTesterBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class ReadCollectionRequestHandlerIT extends GivenWhenThenTesterBase {

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
    public JsonValue doGivenWhen(JsonValue openApi, JsonValue when) {

        Request request = Request.of(when.getJsonObject());

        return new RequestHandlerProvider(openApi.getJsonObject())
                .getRequestHandler(request)
                .process(request)
                .asJsonObject()
                .jsonValue();
    }
}
