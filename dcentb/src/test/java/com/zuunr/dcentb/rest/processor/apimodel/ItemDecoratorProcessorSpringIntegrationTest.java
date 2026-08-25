package com.zuunr.dcentb.rest.processor.apimodel;

import com.zuunr.dcentb.itemdecoratortest.collections.springmanaged.ItemDecorator;
import com.zuunr.dcentb.spring.DcentbApplicationContextHolder;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers the Spring-bean resolution path in ItemDecoratorProcessor.resolveDelegate: when
 * a decorator class is registered as a Spring bean, that bean is used (with whatever
 * Spring injected into it) instead of the reflective config.as(...) construction that
 * ItemDecoratorProcessorTest covers.
 */
class ItemDecoratorProcessorSpringIntegrationTest {

    private AnnotationConfigApplicationContext context;

    @Configuration
    static class TestConfig {
        @Bean
        public DcentbApplicationContextHolder dcentbApplicationContextHolder() {
            return new DcentbApplicationContextHolder();
        }

        @Bean
        public ItemDecorator itemDecorator() {
            return new ItemDecorator("hello from spring");
        }
    }

    @AfterEach
    void tearDown() {
        if (context != null) {
            context.close();
        }
        // Must reset the static holder, or a closed ApplicationContext would leak into
        // every other test in this JVM run (including ItemDecoratorProcessorTest's
        // no-Spring-bean cases), causing spurious IllegalStateExceptions.
        new DcentbApplicationContextHolder().setApplicationContext(null);
    }

    private static JsonValue configFor(String path) {
        return JsonObject.EMPTY
                .put("path", path)
                .put("x-dcentb", JsonObject.EMPTY.put("decoratorBasePackage", "com.zuunr.dcentb.itemdecoratortest"))
                .jsonValue();
    }

    @Test
    void usesSpringManagedDecoratorInsteadOfReflectiveConstruction() {
        context = new AnnotationConfigApplicationContext(TestConfig.class);

        NewStateItemDecorator processor = new NewStateItemDecorator(configFor("/springmanaged/{id}"));

        JsonObject requestContext = JsonObject.EMPTY.put("newState", JsonObject.EMPTY);
        JsonObject result = processor.process(requestContext);

        assertEquals("hello from spring", result.get("newState").getJsonObject().get("greeting").getString());
    }
}
