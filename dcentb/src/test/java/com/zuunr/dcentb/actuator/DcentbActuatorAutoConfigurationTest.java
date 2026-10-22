package com.zuunr.dcentb.actuator;

import com.zuunr.dcentb.async.AsyncProcessingLifecycle;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.health.autoconfigure.actuate.endpoint.HealthEndpointAutoConfiguration;
import org.springframework.boot.health.autoconfigure.contributor.HealthContributorAutoConfiguration;
import org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration;
import org.springframework.boot.micrometer.metrics.autoconfigure.export.simple.SimpleMetricsExportAutoConfiguration;

/**
 * Proves dcentb.actuator.enabled is a real, working kill switch - fast (no embedded server, no
 * Mongo), using Spring Boot's own idiomatic ApplicationContextRunner rather than a full
 * @SpringBootTest. See DcentbActuatorIT for the real-HTTP-level coverage of the enabled case.
 * Supplies its own disabled AsyncProcessingLifecycle bean directly (rather than pulling in
 * AsyncProcessingAutoConfiguration, which needs a real OpenAPI resource and Mongo config) - this
 * test is only about DcentbActuatorAutoConfiguration's own conditional wiring.
 */
class DcentbActuatorAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(AsyncProcessingLifecycle.class, () -> new AsyncProcessingLifecycle(null, null, null, null, null))
            .withConfiguration(AutoConfigurations.of(
                    HealthContributorAutoConfiguration.class,
                    HealthEndpointAutoConfiguration.class,
                    MetricsAutoConfiguration.class,
                    SimpleMetricsExportAutoConfiguration.class,
                    DcentbActuatorAutoConfiguration.class));

    @Test
    void registersItsBeansByDefault() {
        contextRunner.run(context -> {
            org.junit.jupiter.api.Assertions.assertTrue(context.containsBean("asyncProcessingHealthIndicator"));
        });
    }

    @Test
    void dcentbActuatorEnabledFalseRegistersNoneOfItsBeans() {
        contextRunner.withPropertyValues("dcentb.actuator.enabled=false").run(context -> {
            org.junit.jupiter.api.Assertions.assertFalse(context.containsBean("asyncProcessingHealthIndicator"));
            org.junit.jupiter.api.Assertions.assertFalse(context.containsBean("mongoHealthIndicator"));
        });
    }
}
