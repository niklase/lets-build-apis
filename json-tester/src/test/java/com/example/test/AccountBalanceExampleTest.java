package com.example.test;

import com.zuunr.json.JsonNumber;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import com.zuunr.jsontester.GivenWhenThenTesterBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Both the
 */
class AccountBalanceExampleTest extends GivenWhenThenTesterBase {

    /*
     * This method implementation may be copied as-is to any other subclass of GivenWhenThenBaseTester
     */
    static Stream<Path> testFiles() throws Exception {
        return testFiles((Class <? extends GivenWhenThenTesterBase>) new Object(){}.getClass().getEnclosingClass()); // NOSONAR
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
    public JsonValue doGivenWhen(JsonValue given, JsonValue when) {

        // Perform the test (typically calling the method to be tested)
        BigDecimal balance = given.get("balance", 0).getJsonNumber().asBigDecimal();
        BigDecimal withdrawal = when.get("withdrawal").getJsonNumber().asBigDecimal();
        BigDecimal resultingBalance = balance.subtract(withdrawal);

        // Return result (i.e "then" in given-when-then) as com.zuunr.json.JsonValue
        return JsonObject.EMPTY.put("balance", resultingBalance).jsonValue();
    }
}
