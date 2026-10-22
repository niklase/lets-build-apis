package com.zuunr.dcentb.actuator;

import com.zuunr.dcentb.async.AsyncProcessingLifecycle;
import com.zuunr.dcentb.async.changestream.ChangeStreamListener;
import com.zuunr.dcentb.async.leaderelection.LeaderLease;
import com.zuunr.dcentb.async.taskprocessing.ErrorQueueProcessor;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class AsyncProcessingHealthIndicatorTest {

    @Test
    void upWithEnabledFalseWhenAsyncProcessingIsDisabled() {
        AsyncProcessingLifecycle lifecycle = new AsyncProcessingLifecycle(null, null, null, null, null);
        AsyncProcessingHealthIndicator indicator = new AsyncProcessingHealthIndicator(lifecycle);

        Health health = indicator.health();

        assertEquals(Status.UP, health.getStatus());
        assertEquals(Boolean.FALSE, health.getDetails().get("enabled"));
    }

    @Test
    void reportsFullLeaderDetailWhenThisInstanceIsLeader() {
        ChangeStreamListener listener = Mockito.mock(ChangeStreamListener.class);
        Mockito.when(listener.isRunning()).thenReturn(true);
        Instant expiry = Instant.now().plusSeconds(30);
        Mockito.when(listener.currentLease()).thenReturn(LeaderLease.leader("instance-1", 7, expiry));
        ErrorQueueProcessor errorQueueProcessor = Mockito.mock(ErrorQueueProcessor.class);
        Mockito.when(errorQueueProcessor.isRunning()).thenReturn(true);

        AsyncProcessingLifecycle lifecycle = new AsyncProcessingLifecycle(listener, errorQueueProcessor, null, null, null);
        AsyncProcessingHealthIndicator indicator = new AsyncProcessingHealthIndicator(lifecycle);

        Health health = indicator.health();

        assertEquals(Status.UP, health.getStatus());
        assertEquals(Boolean.TRUE, health.getDetails().get("enabled"));
        assertEquals(Boolean.TRUE, health.getDetails().get("running"));
        assertEquals(Boolean.TRUE, health.getDetails().get("leader"));
        assertEquals(7L, health.getDetails().get("generation"));
        assertEquals(expiry.toString(), health.getDetails().get("leaseExpiresAt"));
        assertEquals(Boolean.TRUE, health.getDetails().get("errorQueueProcessorRunning"));
    }

    @Test
    void omitsLeaderOnlyDetailsWhenNotLeader() {
        ChangeStreamListener listener = Mockito.mock(ChangeStreamListener.class);
        Mockito.when(listener.isRunning()).thenReturn(true);
        Mockito.when(listener.currentLease()).thenReturn(LeaderLease.notLeader());

        AsyncProcessingLifecycle lifecycle = new AsyncProcessingLifecycle(listener, null, null, null, null);
        AsyncProcessingHealthIndicator indicator = new AsyncProcessingHealthIndicator(lifecycle);

        Health health = indicator.health();

        assertEquals(Boolean.FALSE, health.getDetails().get("leader"));
        assertFalse(health.getDetails().containsKey("generation"));
        assertFalse(health.getDetails().containsKey("leaseExpiresAt"));
        assertFalse(health.getDetails().containsKey("errorQueueProcessorRunning"));
    }
}
