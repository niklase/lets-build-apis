package com.zuunr.dcentb.spring;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * Gives plain-Java, reflectively-constructed classes (e.g. ItemDecoratorProcessor's
 * resolveDelegate) a way to reach the live Spring ApplicationContext, so a class
 * resolved by convention (Class.forName) can be looked up as a Spring bean before
 * falling back to reflective construction via config.as(...).
 *
 * Assumes exactly one ApplicationContext per JVM. If dcentb ever needs to support
 * multiple independent contexts in one JVM (e.g. Host-header-routed sandboxes), this
 * single static field would need to become a per-tenant lookup instead.
 */
public class DcentbApplicationContextHolder implements ApplicationContextAware {

    private static volatile ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        DcentbApplicationContextHolder.applicationContext = applicationContext;
    }

    public static ApplicationContext get() {
        return applicationContext;
    }
}
