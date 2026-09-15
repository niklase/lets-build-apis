package com.zuunr.dcentb.async.taskprocessing;

import com.zuunr.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * No MongoDB needed — DcentbApplicationContextHolder.get() returns null outside a running Spring
 * context, so resolve() always exercises the reflective (JsonValue)-constructor fallback path here.
 * The Spring-bean-first path mirrors ItemDecoratorProcessor.resolveDelegate exactly and isn't
 * re-tested (that mechanism already has its own test coverage on the ItemDecorator side).
 */
class TaskProcessorResolverTest {

    @Test
    void resolvesViaReflectiveConstructorFallback() {
        JsonObject config = JsonObject.EMPTY.put("name", "audit");

        TaskProcessor resolved = TaskProcessorResolver.resolve(FixtureTaskProcessor.class.getName(), config.jsonValue());

        assertTrue(resolved instanceof FixtureTaskProcessor);
        assertEquals(config.jsonValue(), ((FixtureTaskProcessor) resolved).getConfig());
        assertEquals(TaskResult.Status.SUCCESS, resolved.process(JsonObject.EMPTY).getStatus());
    }

    @Test
    void throwsForAMissingClass() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () ->
                TaskProcessorResolver.resolve("com.zuunr.dcentb.async.taskprocessing.NoSuchClass", JsonObject.EMPTY.jsonValue()));
        assertTrue(e.getMessage().contains("NoSuchClass"));
    }

    @Test
    void throwsForAClassThatDoesNotImplementTaskProcessor() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () ->
                TaskProcessorResolver.resolve("java.lang.String", JsonObject.EMPTY.jsonValue()));
        assertTrue(e.getMessage().contains("does not implement TaskProcessor"));
    }
}
