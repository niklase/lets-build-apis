package com.example.test;

import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import com.zuunr.jsontester.GivenWhenThenTesterBase;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves "maxAttempts" is an independent safety cap that races against "timeoutMillis" - it is
 * NOT scheduled to land near the timeout deadline. A condition that can never be met, given a
 * short maxAttempts and a deliberately long timeoutMillis, must give up as soon as maxAttempts
 * is reached - not wait out the long timeout just to fail at a "tidier" moment.
 * <p>
 * Deliberately not using the testFiles()/@ParameterizedTest discovery pattern the passing
 * example tests use - this fixture is designed to fail.
 */
class RetryMaxAttemptsExampleTest extends GivenWhenThenTesterBase {

    private int attempts;

    @Test
    void maxAttemptsStopsWellBeforeTheLongTimeout() throws Exception {
        testFiles(RetryMaxAttemptsExampleTest.class);

        long start = System.currentTimeMillis();
        assertThrows(AssertionError.class, () -> executeTest(Path.of("max-attempts-caps-before-timeout-test.json")));
        long elapsedMillis = System.currentTimeMillis() - start;

        assertEquals(5, attempts, "should stop at exactly maxAttempts, not before or after");
        assertTrue(elapsedMillis < 5000,
                "should give up almost immediately, long before the 60s timeout budget - took " + elapsedMillis + " ms");
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
