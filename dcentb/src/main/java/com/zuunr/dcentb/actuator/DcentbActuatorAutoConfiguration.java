package com.zuunr.dcentb.actuator;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.zuunr.dcentb.async.AsyncProcessingLifecycle;
import com.zuunr.mongodb.MongoJsonDB;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.PropertySource;

import java.time.Duration;

/**
 * Auto-configures dcentb's health/readiness/metrics support. Gated two ways:
 * {@code @ConditionalOnClass(HealthIndicator.class)} is a defensive fallback in case Actuator is
 * somehow excluded from the classpath despite being a required dependency of dcentb itself (so
 * dcentb still boots cleanly rather than throwing a ClassNotFoundException); the real day-to-day
 * on/off switch is the {@code dcentb.actuator.enabled} property (default true) - one line, no
 * rebuild, no pom surgery, to turn this whole feature off. See docs/health-readiness-metrics.md.
 *
 * <p>{@code asyncProcessingMetricsBinder}/{@code asyncMetricsSampler} take {@code MeterRegistry}
 * as a plain, unconditional constructor dependency rather than guarding it with
 * {@code @ConditionalOnBean(MeterRegistry.class)} - that guard was tried first and silently broke
 * both beans: {@code @ConditionalOnBean} is evaluated against whatever bean definitions already
 * exist at THIS auto-configuration's turn in the processing order, not the final set, and
 * {@code @AutoConfigureAfter}-ing the specific Micrometer auto-configuration class that (sometimes)
 * registers it first didn't reliably fix the ordering either. Since Actuator - and therefore
 * Micrometer core - is a required dependency of dcentb (not optional), Boot's own metrics
 * auto-configuration registering a {@code MeterRegistry} is guaranteed, not conditional, so a
 * plain dependency is both simpler and correct: if it's ever somehow missing, Spring now fails
 * loudly with a clear UnsatisfiedDependencyException instead of silently registering nothing.
 * Confirmed by actually booting dcentb-students-demo and checking /actuator/metrics for
 * dcentb.async.* - this is the fix that made them appear.
 */
@AutoConfiguration
@ConditionalOnClass(HealthIndicator.class)
@ConditionalOnProperty(prefix = "dcentb.actuator", name = "enabled", matchIfMissing = true)
@PropertySource("classpath:com/zuunr/dcentb/actuator/dcentb-actuator-defaults.properties")
public class DcentbActuatorAutoConfiguration {

    @Bean
    // Named "mongoHealthIndicator" deliberately, not e.g. "mongoReadinessHealthIndicator": Spring
    // Boot's own MongoHealthContributorAutoConfiguration (from spring-boot-mongodb, pulled in
    // transitively via com.zuunr:mongodb's dependency on spring-boot-starter-data-mongodb) is
    // @ConditionalOnMissingBean(name = {"mongoHealthIndicator", "mongoHealthContributor"}) - it
    // auto-registers its OWN generic Mongo health indicator otherwise, using Spring Data
    // MongoDB's own connection properties (which dcentb never sets, since dcentb manages its own
    // MongoClient independently via dcentb.mongodb.*) - that indicator connects anonymously to
    // localhost:27017 and calls listDatabases, which fails Unauthorized against dcentb's real,
    // credentialed deployment and drags the whole aggregate /actuator/health down to 503. Taking
    // this exact bean name makes Boot skip registering its broken one entirely, in favor of this
    // one - which correctly uses dcentb's actual configured connection and does a plain ping.
    @ConditionalOnMissingBean(name = "mongoHealthIndicator")
    @ConditionalOnProperty(name = "dcentb.mongodb.db")
    public MongoReadinessHealthIndicator mongoHealthIndicator(
            @Value("${dcentb.mongodb.connection:mongodb://admin:adminpassword@localhost:27017/?authSource=admin}") String mongodbConnectionString,
            @Value("${dcentb.mongodb.db}") String databaseName) {
        // A dedicated, direct Mongo connection - not MongoJsonDBHandle, which is never a Spring
        // bean (it's constructed reflectively per-operation from the REST pipeline's own JSON
        // config - see ApiKeyAuthenticator/DatabaseCommandRunner). Mirrors exactly how
        // AsyncProcessingAutoConfiguration builds its own independent connection, for the same
        // reason: nothing in dcentb exposes a single shared MongoClient as a reusable bean today.
        // Only registered when dcentb.mongodb.db is set as a property (the documented, common
        // case for every real deployment and both demo apps), rather than also replicating the
        // OpenAPI-document db-name fallback RequestHandlerProvider/AsyncProcessingAutoConfiguration
        // each already implement - a third copy of that resolution logic for a case nothing in
        // this repo's own demos actually relies on.
        MongoClient mongoClient = MongoClients.create(mongodbConnectionString);
        MongoDatabase database = mongoClient.getDatabase(databaseName);
        return new MongoReadinessHealthIndicator(new MongoJsonDB(database));
    }

    @Bean
    @ConditionalOnMissingBean
    public AsyncProcessingHealthIndicator asyncProcessingHealthIndicator(AsyncProcessingLifecycle lifecycle) {
        return new AsyncProcessingHealthIndicator(lifecycle);
    }

    @Bean
    @ConditionalOnMissingBean
    public AsyncProcessingMetricsBinder asyncProcessingMetricsBinder(MeterRegistry registry) {
        return new AsyncProcessingMetricsBinder(registry);
    }

    @Bean
    @ConditionalOnMissingBean
    public AsyncMetricsSampler asyncMetricsSampler(AsyncProcessingLifecycle lifecycle, AsyncProcessingMetricsBinder binder,
            // String + Duration.parse(...), not a @Value-bound Duration directly: Boot's own
            // SpringApplication-bootstrapped contexts understand ISO-8601 duration strings via
            // @Value out of the box (ApplicationConversionService), but a plainer Spring test
            // context (e.g. ApplicationContextRunner, used in DcentbActuatorAutoConfigurationTest)
            // does not register that same conversion service - parsing it directly here works
            // identically everywhere, Boot app or plain context, with no reliance on which
            // conversion service happens to be wired in.
            @Value("${dcentb.actuator.async-metrics.sample-interval:PT15S}") String sampleIntervalIso) {
        return new AsyncMetricsSampler(lifecycle, binder, Duration.parse(sampleIntervalIso));
    }
}
