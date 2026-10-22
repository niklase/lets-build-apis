package com.zuunr.dcentb.actuator;

import com.zuunr.mongodb.MongoJsonDB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

/**
 * Readiness signal: can this instance actually serve CRUD traffic right now. Deliberately only in
 * the "readiness" health group (see dcentb-actuator-defaults.properties), never "liveness" - a
 * transient Mongo blip should stop new traffic routing to this instance (readiness), not get it
 * killed and restarted (liveness) - restarting fixes nothing about a database outage and risks a
 * crash-restart loop. See docs/health-readiness-metrics.md.
 */
public class MongoReadinessHealthIndicator implements HealthIndicator {

    private static final Logger LOG = LoggerFactory.getLogger(MongoReadinessHealthIndicator.class);

    private final MongoJsonDB mongoJsonDB;

    public MongoReadinessHealthIndicator(MongoJsonDB mongoJsonDB) {
        this.mongoJsonDB = mongoJsonDB;
    }

    @Override
    public Health health() {
        try {
            mongoJsonDB.ping();
            // No details on the UP path, and a fixed string (never the raw exception message) on
            // the DOWN path below - a guarantee stronger than relying on show-details staying at
            // its safe default ("never"): even if a consumer later sets show-details=always,
            // nothing connection-string-derived can ever appear here.
            return Health.up().build();
        } catch (RuntimeException e) {
            LOG.warn("Mongo readiness ping failed", e); // full exception server-side, logs only
            return Health.down().withDetail("reason", "mongo unreachable").build();
        }
    }
}
