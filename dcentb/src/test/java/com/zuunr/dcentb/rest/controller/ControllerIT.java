package com.zuunr.dcentb.rest.controller;

import com.zuunr.dcentb.rest.Request;
import com.zuunr.dcentb.rest.requesthandler.DcentbGivenWhenThenTester;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import com.zuunr.jsontester.GivenWhenThenTesterBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.util.stream.Stream;

public class ControllerIT extends DcentbGivenWhenThenTester {


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

    RequestHandlerProvider requestHandlerProvider;

    @Override
    public void doGiven(JsonValue given) {
        super.doGiven(given);
        JsonObject config = injectDbSetupIntoConfig(given.getJsonObject().get("config").getJsonObject());
        requestHandlerProvider = new RequestHandlerProvider(config);
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

}
