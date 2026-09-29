package com.example.test;

import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import com.zuunr.jsontester.GivenWhenThenTesterBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Demonstrates "await": a "when" is re-executed on an interval until every "then" in its
 * group matches, simulating polling an eventually-consistent read (e.g. a REST endpoint
 * updated by an async side effect) without a fixed sleep.
 */
class AwaitExampleTest extends GivenWhenThenTesterBase {

    private int attempts;

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
        attempts = 0;
    }

    @Override
    public JsonValue doWhen(JsonValue when) {
        attempts++;
        return JsonObject.EMPTY.put("attempts", attempts).jsonValue();
    }
}
