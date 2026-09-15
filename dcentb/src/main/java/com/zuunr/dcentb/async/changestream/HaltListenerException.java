package com.zuunr.dcentb.async.changestream;

/**
 * Throw this from {@code eventHandler} to permanently halt this {@link ChangeStreamListener} instance
 * — release leadership and stop, rather than the default behavior for any other exception (log it,
 * don't checkpoint, re-run leader election, retry the same event). This is the contract
 * {@link com.zuunr.dcentb.async.taskprocessing.TopicRouter} uses for {@code TaskResult.blockAll} (see
 * docs/async-tasks-processing.md §8.3): "halt everything until a human intervenes" only makes sense
 * as an actual halt, not as an infinite, tightly-looping crash-and-reelect cycle that keeps hitting
 * the same failure. Any {@code eventHandler} — not just topic routing — can use this same contract.
 *
 * <p>Deliberate, known limitation: the halt does not survive a process restart. A new process (or the
 * same one, restarted) will attempt leader election and resume from the last checkpoint again, which
 * means it will hit the same failure and halt again — safe, but not silent. A halt that survives
 * restarts (e.g. a persisted "blocked" flag some other instance also checks before electing) is not
 * implemented; this is flagged, not hidden.
 */
public class HaltListenerException extends RuntimeException {

    public HaltListenerException(String message) {
        super(message);
    }

    public HaltListenerException(String message, Throwable cause) {
        super(message, cause);
    }
}
