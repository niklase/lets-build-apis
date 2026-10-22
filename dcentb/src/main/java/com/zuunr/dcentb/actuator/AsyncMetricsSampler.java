package com.zuunr.dcentb.actuator;

import com.zuunr.dcentb.async.AsyncProcessingLifecycle;
import com.zuunr.dcentb.async.changestream.ChangeStreamListener;
import com.zuunr.dcentb.async.changestream.CheckpointStore;
import com.zuunr.dcentb.async.taskprocessing.ErrorQueueStore;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import java.time.Duration;
import java.time.Instant;

/**
 * Periodically samples change-stream lag and error-queue/dead-letter depth into
 * {@link AsyncProcessingMetricsBinder}'s gauges, on its own daemon thread - the same lifecycle
 * shape already used by {@code ChangeStreamListener}/{@code ErrorQueueProcessor} (start via
 * {@code ApplicationReadyEvent}, stop via {@code @PreDestroy}) rather than Spring's
 * {@code @Scheduled}/{@code @EnableScheduling}, so enabling this feature never imposes Spring's
 * scheduling infrastructure on the consumer's own application context. See
 * docs/health-readiness-metrics.md.
 *
 * <p>Checkpoint and error-queue state are both shared, global Mongo collections (one per
 * {@code streamId}, not per-instance) - every instance samples the same values regardless of
 * which one currently holds leadership, so only the {@code leader} gauge itself differs per
 * instance. A no-op, safely, when async processing is disabled: every
 * {@link AsyncProcessingLifecycle} accessor is null in that case (see its class Javadoc), so each
 * iteration simply finds nothing to sample and the gauges stay at their initial zero values -
 * accurate for a disabled pipeline, not a missing read.
 */
public class AsyncMetricsSampler {

    private static final Logger LOG = LoggerFactory.getLogger(AsyncMetricsSampler.class);

    private final AsyncProcessingLifecycle lifecycle;
    private final AsyncProcessingMetricsBinder binder;
    private final Duration sampleInterval;

    private volatile boolean running = false;
    private Thread thread;

    public AsyncMetricsSampler(AsyncProcessingLifecycle lifecycle, AsyncProcessingMetricsBinder binder, Duration sampleInterval) {
        this.lifecycle = lifecycle;
        this.binder = binder;
        this.sampleInterval = sampleInterval;
    }

    @EventListener(ApplicationReadyEvent.class)
    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        thread = new Thread(this::sampleLoop, "dcentb-async-metrics-sampler");
        thread.setDaemon(true);
        thread.start();
    }

    @PreDestroy
    public synchronized void stop() {
        running = false;
        if (thread != null) {
            thread.interrupt();
            try {
                thread.join(Duration.ofSeconds(5).toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            thread = null;
        }
    }

    public boolean isRunning() {
        return running;
    }

    private void sampleLoop() {
        while (running) {
            try {
                sampleOnce();
            } catch (RuntimeException e) {
                LOG.warn("Error while sampling async-processing metrics - will retry next interval", e);
            }
            sleepQuietly();
        }
    }

    void sampleOnce() {
        ChangeStreamListener listener = lifecycle.getListener();
        if (listener == null) {
            return; // async processing disabled - nothing to sample, gauges stay at 0
        }
        binder.setLeader(listener.currentLease().isLeader());

        CheckpointStore checkpointStore = lifecycle.getCheckpointStore();
        if (checkpointStore != null) {
            checkpointStore.load().ifPresent(checkpoint -> binder.setChangeStreamLagSeconds(
                    Math.max(0, Duration.between(checkpoint.getClusterTimeAsInstant(), Instant.now()).getSeconds())));
        }

        ErrorQueueStore errorQueueStore = lifecycle.getErrorQueueStore();
        if (errorQueueStore != null) {
            binder.setErrorQueueDepth(errorQueueStore.count());
            String deadLetterCollection = lifecycle.getDeadLetterCollection();
            if (deadLetterCollection != null) {
                binder.setDeadLetterCount(errorQueueStore.deadLetterCount(deadLetterCollection));
            }
        }
    }

    private void sleepQuietly() {
        try {
            Thread.sleep(sampleInterval.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
