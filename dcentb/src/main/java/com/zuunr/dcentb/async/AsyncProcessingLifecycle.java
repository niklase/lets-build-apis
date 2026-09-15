package com.zuunr.dcentb.async;

import com.zuunr.dcentb.async.changestream.ChangeStreamListener;
import com.zuunr.dcentb.async.taskprocessing.ErrorQueueProcessor;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

/**
 * Starts {@link ChangeStreamListener} and {@link ErrorQueueProcessor} once the application is fully
 * up (not earlier — starting during context refresh would race other beans still initializing) and
 * stops both on shutdown so leadership is released promptly rather than left to expire via lease TTL.
 * Both are null when {@code x-dcentb.asyncProcessing} isn't configured (see
 * {@link com.zuunr.dcentb.async.config.AsyncProcessingSettings}) — this class is still registered as
 * a bean in that case (simpler than conditionally registering it) but every method is then a no-op.
 */
public class AsyncProcessingLifecycle {

    private final ChangeStreamListener listener;
    private final ErrorQueueProcessor errorQueueProcessor;

    public AsyncProcessingLifecycle(ChangeStreamListener listener, ErrorQueueProcessor errorQueueProcessor) {
        this.listener = listener;
        this.errorQueueProcessor = errorQueueProcessor;
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
