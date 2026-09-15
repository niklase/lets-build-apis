package com.zuunr.dcentb.async.taskprocessing;

import com.zuunr.dcentb.spring.DcentbApplicationContextHolder;
import com.zuunr.json.JsonValue;
import org.springframework.context.ApplicationContext;

/**
 * Resolves a {@link TaskProcessor} instance from a fully-qualified class name, the same two-tier
 * strategy {@code ItemDecoratorProcessor.resolveDelegate} already uses for ItemDecorator: prefer a
 * Spring-managed bean (so a TaskProcessor can use normal {@code @Autowired}/constructor injection —
 * a RestTemplate, a repository, whatever it needs), falling back to reflective construction via a
 * {@code (JsonValue)} constructor if it isn't a Spring bean.
 *
 * <p>Unlike ItemDecorator, the class name here is not derived from a base-package-plus-convention —
 * each subscriber names its {@code taskProcessorClass} explicitly in config
 * (docs/async-tasks-processing.md §9.2). Consequently a missing/wrong class is a real configuration
 * error, not an optional feature someone chose not to write: this throws rather than returning null.
 */
public final class TaskProcessorResolver {

    private TaskProcessorResolver() {
    }

    /**
     * @param config passed to the reflective {@code (JsonValue)} constructor fallback — typically the
     *               subscriber's own config object, so a TaskProcessor can read fields declared
     *               alongside its {@code taskProcessorClass} if it needs to.
     */
    public static TaskProcessor resolve(String fullyQualifiedClassName, JsonValue config) {
        Class<?> rawClass;
        try {
            rawClass = Class.forName(fullyQualifiedClassName);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("TaskProcessor class not found: " + fullyQualifiedClassName, e);
        }

        Class<? extends TaskProcessor> taskProcessorClass;
        try {
            taskProcessorClass = rawClass.asSubclass(TaskProcessor.class);
        } catch (ClassCastException e) {
            throw new IllegalStateException(fullyQualifiedClassName + " does not implement TaskProcessor", e);
        }

        ApplicationContext applicationContext = DcentbApplicationContextHolder.get();
        if (applicationContext != null) {
            TaskProcessor springManagedBean = applicationContext.getBeanProvider(taskProcessorClass).getIfAvailable();
            if (springManagedBean != null) {
                return springManagedBean;
            }
        }

        return config.as(taskProcessorClass);
    }
}
