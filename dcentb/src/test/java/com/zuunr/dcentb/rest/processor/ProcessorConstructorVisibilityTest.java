package com.zuunr.dcentb.rest.processor;

import com.zuunr.json.JsonValue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Request handlers never construct their processors directly - they build the pipeline
 * reflectively, with config.as(SomeProcessor.class) (see ReadCollectionRequestHandler).
 * Whether a non-public constructor survives that call depends on whether JsonValue.as(...)
 * calls setAccessible(true), and that has differed between com.zuunr:json builds carrying
 * the same version number. PostGetCollectionBodyToQueryProcessor's package-private
 * constructor therefore worked on one machine and failed with IllegalAccessException on
 * another, breaking every collection-read request there (GET /{domain}/{collection} and
 * POST .../getCollection) while the item endpoints kept working.
 *
 * Declaring the constructor public removes that dependency on the json build, so this test
 * holds every framework processor to it - the missing modifier is invisible in the module's
 * own tests otherwise, since nothing here instantiates a processor the way a handler does.
 */
class ProcessorConstructorVisibilityTest {

    private static final String PROCESSOR_PACKAGE = "com.zuunr.dcentb.rest.processor";

    @Test
    void everyProcessorHasAPublicJsonValueConstructor() {

        List<String> offenders = new ArrayList<>();

        for (Class<?> processor : processors()) {
            try {
                Constructor<?> constructor = processor.getDeclaredConstructor(JsonValue.class);
                if (!Modifier.isPublic(constructor.getModifiers())) {
                    offenders.add(processor.getName() + ": (JsonValue) constructor is not public");
                }
            } catch (NoSuchMethodException e) {
                offenders.add(processor.getName() + ": no (JsonValue) constructor");
            }
        }

        assertEquals(List.of(), offenders);
    }

    /**
     * Concrete Processor subclasses in {@link #PROCESSOR_PACKAGE} and its subpackages.
     * Abstract bases (Processor, PreOperationAccessController, ItemDecoratorProcessor) are
     * excluded by the scanner, and so are application-supplied processors outside this
     * package - a project's own item decorator is its own concern.
     */
    private static Set<Class<?>> processors() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(Processor.class));

        Set<Class<?>> processors = new java.util.LinkedHashSet<>();
        for (BeanDefinition candidate : scanner.findCandidateComponents(PROCESSOR_PACKAGE)) {
            try {
                processors.add(Class.forName(candidate.getBeanClassName()));
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("Scanned class cannot be loaded: " + candidate.getBeanClassName(), e);
            }
        }
        assertFalse(processors.isEmpty(), "Found no processors to check in " + PROCESSOR_PACKAGE);
        return processors;
    }
}
