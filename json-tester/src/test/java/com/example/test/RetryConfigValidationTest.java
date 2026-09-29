package com.example.test;

import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import com.zuunr.jsontester.GivenWhenThenTesterBase;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the structural invariants around "steps"/"retry" are enforced loudly at parse time,
 * not silently ignored or misinterpreted: 'retry' is rejected on the legacy flat 'when' shape
 * (it only makes sense as a group-level property of the explicit 'steps' shape - see
 * GivenWhenThenTesterBase's Javadoc), and a malformed 'steps' array (no leading 'when', a
 * 'when' anywhere but first, or no 'then' at all) is rejected rather than silently accepted.
 * <p>
 * Deliberately not using the testFiles()/@ParameterizedTest discovery pattern the passing
 * example tests use - every fixture here is designed to fail.
 */
class RetryConfigValidationTest extends GivenWhenThenTesterBase {

    private int attempts;

    @Test
    void retryOnLegacyWhenItemIsRejected() throws Exception {
        testFiles(RetryConfigValidationTest.class);
        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> executeTest(Path.of("retry-on-legacy-when-is-rejected.json")));
        assertTrue(thrown.getMessage().contains("'retry' is only valid inside a 'steps' group"), thrown.getMessage());
    }

    @Test
    void stepsMustStartWithWhen() throws Exception {
        testFiles(RetryConfigValidationTest.class);
        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> executeTest(Path.of("steps-must-start-with-when.json")));
        assertTrue(thrown.getMessage().contains("'steps' must start with a 'when'"), thrown.getMessage());
    }

    @Test
    void whenMayOnlyAppearOnceInSteps() throws Exception {
        testFiles(RetryConfigValidationTest.class);
        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> executeTest(Path.of("steps-when-must-appear-only-once.json")));
        assertTrue(thrown.getMessage().contains("'when' may only appear once"), thrown.getMessage());
    }

    @Test
    void stepsWithoutAThenIsRejected() throws Exception {
        testFiles(RetryConfigValidationTest.class);
        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> executeTest(Path.of("steps-without-then.json")));
        assertTrue(thrown.getMessage().contains("must contain at least one 'then'"), thrown.getMessage());
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
