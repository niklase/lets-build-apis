package com.zuunr.dcentb.actuator;

import com.zuunr.dcentb.async.AsyncProcessingLifecycle;
import com.zuunr.dcentb.async.changestream.ChangeStreamListener;
import com.zuunr.dcentb.async.changestream.Checkpoint;
import com.zuunr.dcentb.async.changestream.CheckpointStore;
import com.zuunr.dcentb.async.leaderelection.LeaderLease;
import com.zuunr.dcentb.async.taskprocessing.ErrorQueueStore;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.bson.BsonDocument;
import org.bson.BsonTimestamp;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AsyncMetricsSamplerTest {

    @Test
    void sampleOnceIsANoOpWhenAsyncProcessingIsDisabled() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AsyncProcessingLifecycle lifecycle = new AsyncProcessingLifecycle(null, null, null, null, null);
        AsyncMetricsSampler sampler = new AsyncMetricsSampler(lifecycle, new AsyncProcessingMetricsBinder(registry), Duration.ofSeconds(15));

        sampler.sampleOnce(); // must not throw, must leave every gauge at its initial 0

        assertEquals(0.0, registry.get("dcentb.async.leader").gauge().value());
        assertEquals(0.0, registry.get("dcentb.async.errorqueue.depth").gauge().value());
    }

    @Test
    void sampleOnceUpdatesEveryGaugeFromSharedMongoState() {
        ChangeStreamListener listener = Mockito.mock(ChangeStreamListener.class);
        Mockito.when(listener.currentLease()).thenReturn(LeaderLease.leader("instance-1", 1, Instant.now().plusSeconds(30)));

        Instant checkpointTime = Instant.now().minusSeconds(5);
        Checkpoint checkpoint = new Checkpoint(new BsonDocument(), new BsonTimestamp((int) checkpointTime.getEpochSecond(), 0));
        CheckpointStore checkpointStore = Mockito.mock(CheckpointStore.class);
        Mockito.when(checkpointStore.load()).thenReturn(Optional.of(checkpoint));

        ErrorQueueStore errorQueueStore = Mockito.mock(ErrorQueueStore.class);
        Mockito.when(errorQueueStore.count()).thenReturn(3L);
        Mockito.when(errorQueueStore.deadLetterCount("dead-letters")).thenReturn(1L);

        AsyncProcessingLifecycle lifecycle = new AsyncProcessingLifecycle(listener, null, checkpointStore, errorQueueStore, "dead-letters");
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AsyncMetricsSampler sampler = new AsyncMetricsSampler(lifecycle, new AsyncProcessingMetricsBinder(registry), Duration.ofSeconds(15));

        sampler.sampleOnce();

        assertEquals(1.0, registry.get("dcentb.async.leader").gauge().value());
        assertEquals(3.0, registry.get("dcentb.async.errorqueue.depth").gauge().value());
        assertEquals(1.0, registry.get("dcentb.async.errorqueue.deadletter.count").gauge().value());
        // Lag should be roughly 5 seconds (checkpointTime was 5s in the past) - allow slack for test runtime.
        assertEquals(5.0, registry.get("dcentb.async.changestream.lag.seconds").gauge().value(), 2.0);
    }
}
