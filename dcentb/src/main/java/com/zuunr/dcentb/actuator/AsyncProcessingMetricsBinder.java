package com.zuunr.dcentb.actuator;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Registers Micrometer gauges for async-pipeline state - leader flag, change-stream lag,
 * error-queue depth, dead-letter count - backed by the plain fields this class exposes setters
 * for. {@link AsyncMetricsSampler} updates those fields on a timer; registering the gauges
 * themselves is instant and side-effect-free (the standard Micrometer
 * {@code Gauge.builder(...).register()} pattern reads a field/supplier on each scrape, not at
 * registration time). Always registered when a {@code MeterRegistry} is present, regardless of
 * whether async processing is enabled - the gauges simply stay at their initial zero values when
 * it's disabled, which is an accurate statement, not a missing one.
 */
public class AsyncProcessingMetricsBinder {

    private final AtomicLong leader = new AtomicLong(0);
    private final AtomicLong changeStreamLagSeconds = new AtomicLong(0);
    private final AtomicLong errorQueueDepth = new AtomicLong(0);
    private final AtomicLong deadLetterCount = new AtomicLong(0);

    public AsyncProcessingMetricsBinder(MeterRegistry registry) {
        Gauge.builder("dcentb.async.leader", leader, AtomicLong::get)
                .description("1 if this instance currently holds async-processing leadership, else 0")
                .register(registry);
        Gauge.builder("dcentb.async.changestream.lag.seconds", changeStreamLagSeconds, AtomicLong::get)
                .description("Seconds between now and the last persisted change-stream checkpoint")
                .register(registry);
        Gauge.builder("dcentb.async.errorqueue.depth", errorQueueDepth, AtomicLong::get)
                .description("Total documents currently in the error queue")
                .register(registry);
        Gauge.builder("dcentb.async.errorqueue.deadletter.count", deadLetterCount, AtomicLong::get)
                .description("Total documents ever moved to the dead-letter collection")
                .register(registry);
    }

    void setLeader(boolean isLeader) {
        leader.set(isLeader ? 1 : 0);
    }

    void setChangeStreamLagSeconds(long seconds) {
        changeStreamLagSeconds.set(seconds);
    }

    void setErrorQueueDepth(long depth) {
        errorQueueDepth.set(depth);
    }

    void setDeadLetterCount(long count) {
        deadLetterCount.set(count);
    }
}
