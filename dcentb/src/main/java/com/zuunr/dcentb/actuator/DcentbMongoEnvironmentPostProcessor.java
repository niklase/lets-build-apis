package com.zuunr.dcentb.actuator;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.ClassUtils;

import java.util.Map;

/**
 * Defends against a credential-leak gap, not a live hole: {@code dcentb.mongodb.connection} (the
 * property {@code DcentbAutoConfiguration}/{@code AsyncProcessingAutoConfiguration} both read,
 * a cleartext connection string containing a username/password, injected via plain {@code @Value}
 * rather than {@code @ConfigurationProperties}) is never redacted by Spring Boot Actuator's
 * {@code /actuator/env} endpoint today. That endpoint sanitizes a property VALUE whenever its
 * KEY matches a default regex list ({@code password|secret|key|token|.*credentials.*}) -
 * {@code "dcentb.mongodb.connection"} matches none of those, so the raw connection string would
 * render in cleartext for anyone who can reach {@code /actuator/env}.
 *
 * <p>{@code /actuator/env} is NOT exposed over the web by default - {@code
 * management.endpoints.web.exposure.include} defaults to {@code health} only, and dcentb's own
 * shipped defaults (see {@code dcentb-actuator-defaults.properties}) never widen that. So today,
 * with dcentb's own defaults, this gap is dormant. This class exists so it STAYS dormant even if
 * a consumer later widens exposure (e.g. {@code management.endpoints.web.exposure.include=env} or
 * {@code *}) without knowing about this specific property: it appends
 * {@code dcentb.mongodb.connection} to {@code management.endpoint.env.additional-keys-to-sanitize}
 * - the list Actuator's own {@code EnvironmentEndpoint} already supports for exactly this case -
 * merging with, rather than overwriting, whatever the consumer already configured there.
 *
 * <p><b>How the merge works, and its one known limitation</b>: this reads the current effective
 * value of {@code management.endpoint.env.additional-keys-to-sanitize} (so whatever the consumer
 * already set in their own {@code application.properties}/{@code .yml} is captured), appends this
 * class's one key if not already present, and adds the combined result as a NEW property source
 * at the FRONT of the environment (highest priority) - this is what makes the merged value the one
 * actually seen, rather than being shadowed by the consumer's original, unmerged source which is
 * still there, unchanged, at its own position. The one gap this doesn't close: a property source
 * added with even higher priority AFTER this post-processor runs (most commonly, a
 * {@code --management.endpoint.env.additional-keys-to-sanitize=...} command-line argument) would
 * still win outright and lose this merge - an inherent limit of this whole class of "defensively
 * extend a property I don't own" technique, not specific to this implementation.
 *
 * <p>Registered via the classic {@code META-INF/spring.factories} {@code EnvironmentPostProcessor}
 * mechanism (not {@code AutoConfiguration.imports} - this runs too early in startup for that newer
 * mechanism, before there is a Spring context to apply {@code @Conditional*} against). Because of
 * that, this class guards its own Actuator dependency manually via {@link ClassUtils#isPresent}
 * rather than an annotation, so it degrades to a no-op instead of throwing if Actuator genuinely
 * isn't on the classpath. {@code dcentb.actuator.enabled} does NOT affect this class - it is
 * deliberately exactly that: a defense against SOMEONE ELSE enabling {@code /env}, so it stays
 * active independent of whether dcentb's own health/metrics feature (gated by that property) is
 * itself turned on.
 *
 * <p>See docs/health-readiness-metrics.md "Security defaults" for the consumer-facing explanation
 * of the same gap.
 */
public class DcentbMongoEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final String SANITIZE_KEYS_PROPERTY = "management.endpoint.env.additional-keys-to-sanitize";
    private static final String LEAKY_KEY = "dcentb.mongodb.connection";
    private static final String PROPERTY_SOURCE_NAME = "dcentbMongoConnectionSanitizeDefaults";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!ClassUtils.isPresent("org.springframework.boot.health.contributor.HealthIndicator", null)) {
            return; // Actuator not on the classpath at all - nothing to defend
        }

        String existing = environment.getProperty(SANITIZE_KEYS_PROPERTY);
        if (existing != null && containsKey(existing, LEAKY_KEY)) {
            return; // already covers this key (explicitly, or from an earlier run of this same post-processor)
        }

        String merged = (existing == null || existing.isBlank()) ? LEAKY_KEY : existing + "," + LEAKY_KEY;
        environment.getPropertySources().addFirst(
                new MapPropertySource(PROPERTY_SOURCE_NAME, Map.of(SANITIZE_KEYS_PROPERTY, merged)));
    }

    private static boolean containsKey(String commaSeparatedKeys, String key) {
        for (String existingKey : commaSeparatedKeys.split(",")) {
            if (existingKey.trim().equals(key)) {
                return true;
            }
        }
        return false;
    }
}
