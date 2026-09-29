package com.example.test;

import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import com.zuunr.jsontester.GivenWhenThenTesterBase;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Proves "retry" gives up rather than hanging: when a condition never becomes true within the
 * configured timeout, executeTest still fails with a normal assertion diff on the final attempt,
 * same as a non-retried mismatch would.
 * <p>
 * Deliberately not using the testFiles()/@ParameterizedTest discovery pattern the other example
 * tests use - this file's JSON fixture is designed to fail, so it's invoked explicitly here
 * instead of being auto-discovered as a test that must pass.
 */
class RetryTimeoutExampleTest extends GivenWhenThenTesterBase {

    private int attempts;

    @Test
    void retryThatNeverMatchesEventuallyFailsRatherThanHanging() throws Exception {
        testFiles(RetryTimeoutExampleTest.class); // primes the resource-folder lookup executeTest relies on
        assertThrows(AssertionError.class, () -> executeTest(Path.of("never-matches-test.json")));
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
