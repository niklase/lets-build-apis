package com.zuunr.dcentb.actuator;

import com.zuunr.dcentb.async.AsyncProcessingLifecycle;
import com.zuunr.dcentb.async.changestream.ChangeStreamListener;
import com.zuunr.dcentb.async.leaderelection.LeaderLease;
import com.zuunr.dcentb.async.taskprocessing.ErrorQueueProcessor;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

/**
 * Informational leader-election/change-stream/error-queue state - never gates readiness or
 * liveness (deliberately absent from both groups in dcentb-actuator-defaults.properties). A
 * non-leader instance is completely healthy and should keep serving HTTP traffic; only the elected
 * leader runs the change-stream listener at all, so "not leader" is the normal steady state for
 * every instance except one in a multi-instance deployment. See docs/health-readiness-metrics.md.
 *
 * <p>Deliberately does not call CheckpointStore.load() or ErrorQueueStore.count() - both are real
 * Mongo round-trips, and doing them on every /actuator/health hit would make health-check latency
 * depend on Mongo. Those numbers are Micrometer gauges instead (see AsyncProcessingMetricsBinder/
 * AsyncMetricsSampler), sampled on a timer; this indicator only reads values that are already free
 * (isRunning(), currentLease()).
 */
public class AsyncProcessingHealthIndicator implements HealthIndicator {

    private final AsyncProcessingLifecycle lifecycle;

    public AsyncProcessingHealthIndicator(AsyncProcessingLifecycle lifecycle) {
        this.lifecycle = lifecycle;
    }

    @Override
    public Health health() {
        ChangeStreamListener listener = lifecycle.getListener();
        if (listener == null) {
            return Health.up().withDetail("enabled", false).build();
        }

        LeaderLease lease = listener.currentLease();
        Health.Builder builder = Health.up()
                .withDetail("enabled", true)
                .withDetail("running", listener.isRunning())
                .withDetail("leader", lease.isLeader());
        if (lease.isLeader()) {
            builder.withDetail("leaseExpiresAt", lease.getLeaseExpiresAt().toString())
                   .withDetail("generation", lease.getGeneration());
        }

        ErrorQueueProcessor errorQueueProcessor = lifecycle.getErrorQueueProcessor();
        if (errorQueueProcessor != null) {
            builder.withDetail("errorQueueProcessorRunning", errorQueueProcessor.isRunning());
        }
        return builder.build();
    }
}
