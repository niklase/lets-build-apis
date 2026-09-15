package com.zuunr.dcentb.async.taskprocessing;

import com.zuunr.dcentb.async.leaderelection.LeaderElector;
import com.zuunr.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Background reprocessing for {@link ErrorQueueStore}: polls for items whose {@code nextAttemptAt}
 * has passed, re-invokes the right subscriber via {@link TopicRouter#redeliver}, and either deletes
 * the item (success), reschedules it with exponential backoff (failure, under
 * {@code maxErrorQueueRequeues}), or moves it to a dead-letter collection (failure, at the limit).
 * See docs/async-tasks-processing.md §9.3 step 7.
 *
 * <p><b>Leader-gated, but does not itself elect or renew.</b> This class only calls
 * {@link LeaderElector#isStillLeader}, a read-only check — it relies on something else (in practice,
 * the {@code ChangeStreamListener} sharing the same {@link LeaderElector} instance and streamId) to
 * actually be calling {@link LeaderElector#tryAcquireOrRenew} on a heartbeat. This is deliberate: one
 * component renewing the lease, not two independently-scheduled ones — consistent with "just one
 * listener, one leader election" (docs/async-tasks-processing.md §2.1). A standalone
 * {@code ErrorQueueProcessor} with no such renewer running will simply never see itself as leader and
 * never do anything; that is the correct, safe behavior, not a bug to work around.
 *
 * <p><b>{@code BLOCK_ALL} from a redelivered item halts only this processor's own loop</b> — it does
 * not (yet) reach across to halt a co-located {@code ChangeStreamListener} too, even though both are
 * conceptually "this instance's async processing." A known, deliberate scope gap — see
 * {@code HaltListenerException}'s Javadoc for the equivalent honesty about that class not surviving a
 * restart; this is the same kind of gap, not hidden.
 */
public final class ErrorQueueProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(ErrorQueueProcessor.class);
    private static final int BATCH_SIZE = 50;

    private final ErrorQueueStore errorQueueStore;
    private final LeaderElector leaderElector;
    private final TopicRouter topicRouter;
    private final String deadLetterCollection;
    private final int maxErrorQueueRequeues;
    private final Duration pollInterval;
    private final Duration baseBackoff;
    private final Duration maxBackoff;

    private volatile boolean running = false;
    private Thread thread;

    public ErrorQueueProcessor(ErrorQueueStore errorQueueStore, LeaderElector leaderElector, TopicRouter topicRouter,
                                String deadLetterCollection, int maxErrorQueueRequeues, Duration pollInterval,
                                Duration baseBackoff, Duration maxBackoff) {
        this.errorQueueStore = errorQueueStore;
        this.leaderElector = leaderElector;
        this.topicRouter = topicRouter;
        this.deadLetterCollection = deadLetterCollection;
        this.maxErrorQueueRequeues = maxErrorQueueRequeues;
        this.pollInterval = pollInterval;
        this.baseBackoff = baseBackoff;
        this.maxBackoff = maxBackoff;
    }

    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        thread = new Thread(this::pollLoop, "error-queue-processor");
        thread.setDaemon(true);
        thread.start();
    }

    public synchronized void stop() {
        running = false;
        if (thread != null) {
            thread.interrupt();
            try {
                thread.join(Duration.ofSeconds(10).toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            thread = null;
        }
    }

    private void pollLoop() {
        while (running) {
            if (leaderElector.isStillLeader()) {
                try {
                    if (!processDueBatch()) {
                        return; // BLOCK_ALL was hit — halt this processor permanently
                    }
                } catch (RuntimeException e) {
                    LOG.error("Error while processing the error queue — will retry next poll", e);
                }
            }
            sleepQuietly(pollInterval);
        }
    }

    /** @return false if a BLOCK_ALL was encountered and this processor should stop entirely. */
    private boolean processDueBatch() {
        List<JsonObject> items = errorQueueStore.fetchDueBatch(BATCH_SIZE);
        for (JsonObject item : items) {
            if (!running || !leaderElector.isStillLeader()) {
                return true;
            }
            if (!processOne(item)) {
                return false;
            }
        }
        return true;
    }

    /** @return false if this item's result was BLOCK_ALL. */
    private boolean processOne(JsonObject item) {
        String id = item.get("_id").getString();
        String topic = item.get("topic").getString();
        String subscriber = item.get("subscriber").getString();
        JsonObject event = item.get("event").getJsonObject();
        int attempts = item.get("attempts").getInteger();

        TaskResult result = topicRouter.redeliver(topic, subscriber, event);

        switch (result.getStatus()) {
            case SUCCESS:
                LOG.info("Error-queue item for topic '{}' subscriber '{}' succeeded on redelivery (attempt {})", topic, subscriber, attempts);
                errorQueueStore.delete(id);
                return true;
            case BLOCK_ALL:
                LOG.error("BLOCK_ALL from redelivering topic '{}' subscriber '{}' — halting this error-queue processor: {}",
                        topic, subscriber, result.getReason(), result.getCause());
                return false;
            case RETRY:
            case ERROR_QUEUE:
            default:
                int newAttempts = attempts + 1;
                if (newAttempts >= maxErrorQueueRequeues) {
                    LOG.error("Error-queue item for topic '{}' subscriber '{}' exhausted {} attempts — moving to dead letter: {}",
                            topic, subscriber, newAttempts, result.getReason(), result.getCause());
                    errorQueueStore.moveToDeadLetter(item, result, deadLetterCollection);
                } else {
                    Instant nextAttemptAt = Instant.now().plus(backoffFor(newAttempts));
                    LOG.warn("Error-queue item for topic '{}' subscriber '{}' failed redelivery (attempt {}/{}), rescheduled for {}: {}",
                            topic, subscriber, newAttempts, maxErrorQueueRequeues, nextAttemptAt, result.getReason());
                    errorQueueStore.rescheduleAfterFailure(id, newAttempts, nextAttemptAt, result);
                }
                return true;
        }
    }

    private Duration backoffFor(int attempts) {
        long millis = baseBackoff.toMillis() * (1L << Math.min(attempts - 1, 20));
        Duration delay = Duration.ofMillis(millis);
        return delay.compareTo(maxBackoff) > 0 ? maxBackoff : delay;
    }

    private void sleepQuietly(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
