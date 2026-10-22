package com.zuunr.dcentb.actuator;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AsyncProcessingMetricsBinderTest {

    @Test
    void gaugesStartAtZeroAndReflectSetterUpdates() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AsyncProcessingMetricsBinder binder = new AsyncProcessingMetricsBinder(registry);

        assertEquals(0.0, registry.get("dcentb.async.leader").gauge().value());
        assertEquals(0.0, registry.get("dcentb.async.changestream.lag.seconds").gauge().value());
        assertEquals(0.0, registry.get("dcentb.async.errorqueue.depth").gauge().value());
        assertEquals(0.0, registry.get("dcentb.async.errorqueue.deadletter.count").gauge().value());

        binder.setLeader(true);
        binder.setChangeStreamLagSeconds(42);
        binder.setErrorQueueDepth(3);
        binder.setDeadLetterCount(1);

        assertEquals(1.0, registry.get("dcentb.async.leader").gauge().value());
        assertEquals(42.0, registry.get("dcentb.async.changestream.lag.seconds").gauge().value());
        assertEquals(3.0, registry.get("dcentb.async.errorqueue.depth").gauge().value());
        assertEquals(1.0, registry.get("dcentb.async.errorqueue.deadletter.count").gauge().value());
    }
}
