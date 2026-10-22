package com.zuunr.dcentb.async;

import com.zuunr.dcentb.async.changestream.ChangeStreamListener;
import com.zuunr.dcentb.async.changestream.CheckpointStore;
import com.zuunr.dcentb.async.taskprocessing.ErrorQueueProcessor;
import com.zuunr.dcentb.async.taskprocessing.ErrorQueueStore;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

/**
 * Starts {@link ChangeStreamListener} and {@link ErrorQueueProcessor} once the application is fully
 * up (not earlier — starting during context refresh would race other beans still initializing) and
 * stops both on shutdown so leadership is released promptly rather than left to expire via lease TTL.
 * Every field is null when {@code x-dcentb.asyncProcessing} isn't configured (see
 * {@link com.zuunr.dcentb.async.config.AsyncProcessingSettings}) — this class is still registered as
 * a bean in that case (simpler than conditionally registering it) but every method is then a no-op.
 *
 * <p>{@code checkpointStore}/{@code errorQueueStore}/{@code deadLetterCollection} are carried through
 * (rather than reconstructed) purely so health/metrics code elsewhere (see
 * {@code com.zuunr.dcentb.actuator}) can read checkpoint lag and error-queue depth off the same Mongo
 * connection this class already holds, instead of opening a second one.
 */
public class AsyncProcessingLifecycle {

    private final ChangeStreamListener listener;
    private final ErrorQueueProcessor errorQueueProcessor;
    private final CheckpointStore checkpointStore;
    private final ErrorQueueStore errorQueueStore;
    private final String deadLetterCollection;

    public AsyncProcessingLifecycle(ChangeStreamListener listener, ErrorQueueProcessor errorQueueProcessor,
                                     CheckpointStore checkpointStore, ErrorQueueStore errorQueueStore,
                                     String deadLetterCollection) {
        this.listener = listener;
        this.errorQueueProcessor = errorQueueProcessor;
        this.checkpointStore = checkpointStore;
        this.errorQueueStore = errorQueueStore;
        this.deadLetterCollection = deadLetterCollection;
    }

    /** Null when {@code x-dcentb.asyncProcessing} isn't configured - see class Javadoc. */
    public ChangeStreamListener getListener() {
        return listener;
    }

    /** Null when {@code x-dcentb.asyncProcessing} isn't configured - see class Javadoc. */
    public ErrorQueueProcessor getErrorQueueProcessor() {
        return errorQueueProcessor;
    }

    /** Null when {@code x-dcentb.asyncProcessing} isn't configured - see class Javadoc. */
    public CheckpointStore getCheckpointStore() {
        return checkpointStore;
    }

    /** Null when {@code x-dcentb.asyncProcessing} isn't configured - see class Javadoc. */
    public ErrorQueueStore getErrorQueueStore() {
        return errorQueueStore;
    }

    /** Null when {@code x-dcentb.asyncProcessing} isn't configured - see class Javadoc. */
    public String getDeadLetterCollection() {
        return deadLetterCollection;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (listener != null) {
            listener.start();
        }
        if (errorQueueProcessor != null) {
            errorQueueProcessor.start();
        }
    }

    @PreDestroy
    public void stop() {
        if (errorQueueProcessor != null) {
            errorQueueProcessor.stop();
        }
        if (listener != null) {
            listener.stop();
        }
    }
}
