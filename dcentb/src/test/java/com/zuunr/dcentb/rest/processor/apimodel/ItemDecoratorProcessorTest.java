package com.zuunr.dcentb.rest.processor.apimodel;

import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers ItemDecoratorProcessor's convention-based resolution (shared by both
 * subclasses) and the currentState/newState <-> itemState round trip each subclass is
 * responsible for: base-package configurability, optional-when-missing behavior (both
 * for an unconfigured base package and for a collection with no matching class),
 * hyphenated/nested collection names, and that a malformed decorator class still fails
 * loudly.
 */
class ItemDecoratorProcessorTest {

    private static final String BASE_PACKAGE = "com.zuunr.dcentb.itemdecoratortest";

    private static JsonValue configFor(String path, String basePackage) {
        JsonObject config = JsonObject.EMPTY.put("path", path);
        if (basePackage != null) {
            config = config.put("x-dcentb", JsonObject.EMPTY.put("decoratorBasePackage", basePackage));
        }
        return config.jsonValue();
    }

    @Test
    void currentStateItemDecoratorRoundTripsThroughItemState() {
        CurrentStateItemDecorator processor = new CurrentStateItemDecorator(configFor("/widgets/{id}", BASE_PACKAGE));

        JsonObject requestContext = JsonObject.EMPTY.put("currentState", JsonObject.EMPTY.put("foo", "bar"));
        JsonObject result = processor.process(requestContext);

        assertTrue(result.get("currentState").getJsonObject().get("decorated").getBoolean());
        assertEquals("bar", result.get("currentState").getJsonObject().get("foo").getString());
        assertFalse(result.containsKey("itemState"));
        assertFalse(result.containsKey("newState"));
    }

    @Test
    void newStateItemDecoratorRoundTripsThroughItemState() {
        NewStateItemDecorator processor = new NewStateItemDecorator(configFor("/widgets/{id}", BASE_PACKAGE));

        JsonObject requestContext = JsonObject.EMPTY.put("newState", JsonObject.EMPTY.put("foo", "bar"));
        JsonObject result = processor.process(requestContext);

        assertTrue(result.get("newState").getJsonObject().get("decorated").getBoolean());
        assertEquals("bar", result.get("newState").getJsonObject().get("foo").getString());
        assertFalse(result.containsKey("itemState"));
        assertFalse(result.containsKey("currentState"));
    }

    @Test
    void resolvesNestedHyphenatedCollectionName() {
        NewStateItemDecorator processor = new NewStateItemDecorator(configFor("/domain-1/houses/{id}", BASE_PACKAGE));

        JsonObject requestContext = JsonObject.EMPTY.put("newState", JsonObject.EMPTY);
        JsonObject result = processor.process(requestContext);

        assertTrue(result.get("newState").getJsonObject().get("decorated").getBoolean());
    }

    @Test
    void noOpWhenBasePackageNotConfigured() {
        CurrentStateItemDecorator processor = new CurrentStateItemDecorator(configFor("/widgets/{id}", null));

        JsonObject requestContext = JsonObject.EMPTY.put("currentState", JsonObject.EMPTY.put("foo", "bar"));
        JsonObject result = processor.process(requestContext);

        assertEquals(requestContext, result);
        assertFalse(result.containsKey("itemState"));
    }

    @Test
    void noOpWhenNoMatchingClassExistsForCollection() {
        CurrentStateItemDecorator processor = new CurrentStateItemDecorator(configFor("/students/{id}", BASE_PACKAGE));

        JsonObject requestContext = JsonObject.EMPTY.put("currentState", JsonObject.EMPTY.put("foo", "bar"));
        JsonObject result = processor.process(requestContext);

        assertEquals(requestContext, result);
        assertFalse(result.containsKey("itemState"));
    }

    @Test
    void malformedDecoratorClassStillThrows() {
        JsonValue config = configFor("/broken/{id}", BASE_PACKAGE);

        assertThrows(IllegalArgumentException.class, () -> new CurrentStateItemDecorator(config));
    }
}
