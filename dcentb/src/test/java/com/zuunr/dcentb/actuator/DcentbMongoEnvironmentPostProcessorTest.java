package com.zuunr.dcentb.actuator;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DcentbMongoEnvironmentPostProcessorTest {

    private static final String PROPERTY = "management.endpoint.env.additional-keys-to-sanitize";
    private final DcentbMongoEnvironmentPostProcessor postProcessor = new DcentbMongoEnvironmentPostProcessor();

    @Test
    void addsTheKeyWhenNothingWasConfigured() {
        MockEnvironment environment = new MockEnvironment();

        postProcessor.postProcessEnvironment(environment, null);

        assertEquals("dcentb.mongodb.connection", environment.getProperty(PROPERTY));
    }

    @Test
    void extendsAnExistingListRatherThanReplacingIt() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty(PROPERTY, "some.other.secret,another.key");

        postProcessor.postProcessEnvironment(environment, null);

        assertEquals("some.other.secret,another.key,dcentb.mongodb.connection", environment.getProperty(PROPERTY));
    }

    @Test
    void doesNotDuplicateTheKeyIfAlreadyPresent() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty(PROPERTY, "foo,dcentb.mongodb.connection,bar");

        postProcessor.postProcessEnvironment(environment, null);

        assertEquals("foo,dcentb.mongodb.connection,bar", environment.getProperty(PROPERTY));
    }
}
