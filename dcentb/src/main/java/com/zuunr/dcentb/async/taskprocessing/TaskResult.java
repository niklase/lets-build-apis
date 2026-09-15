package com.zuunr.dcentb.async.taskprocessing;

/**
 * What a {@link TaskProcessor} decided about one event, per docs/async-tasks-processing.md §8.3.
 * Four outcomes, not a boolean, because "succeeded," "transient, retry me," "give up on this one
 * item but keep going," and "stop everything" are genuinely different decisions with different
 * blast radii — see {@link TopicRouter} for exactly what each one causes to happen.
 */
public final class TaskResult {

    public enum Status { SUCCESS, RETRY, ERROR_QUEUE, BLOCK_ALL }

    private final Status status;
    private final String reason;
    private final Throwable cause;

    private TaskResult(Status status, String reason, Throwable cause) {
        this.status = status;
        this.reason = reason;
        this.cause = cause;
    }

    public static TaskResult success() {
        return new TaskResult(Status.SUCCESS, null, null);
    }

    /**
     * Request exactly one immediate inline retry. If that retry also fails (RETRY, an exception, or
     * a null return again), it is escalated to {@link #errorQueue} automatically — this is the
     * confirmed, final policy (docs/async-tasks-processing.md §8.3), not a default a caller can
     * override.
     */
    public static TaskResult retry(String reason) {
        return new TaskResult(Status.RETRY, reason, null);
    }

    public static TaskResult retry(String reason, Throwable cause) {
        return new TaskResult(Status.RETRY, reason, cause);
    }

    /** Give up on this one item (for this one subscriber) without holding up anything else. */
    public static TaskResult errorQueue(String reason) {
        return new TaskResult(Status.ERROR_QUEUE, reason, null);
    }

    public static TaskResult errorQueue(String reason, Throwable cause) {
        return new TaskResult(Status.ERROR_QUEUE, reason, cause);
    }

    /** Halt this entire listener instance — not just this item, not just this subscriber. */
    public static TaskResult blockAll(String reason) {
        return new TaskResult(Status.BLOCK_ALL, reason, null);
    }

    public static TaskResult blockAll(String reason, Throwable cause) {
        return new TaskResult(Status.BLOCK_ALL, reason, cause);
    }

    public Status getStatus() {
        return status;
    }

    /** Human-readable, for logs and error-queue documents. Null only for {@link #success()}. */
    public String getReason() {
        return reason;
    }

    /** May be null even for a non-success result (e.g. an explicit {@link #retry(String)} call). */
    public Throwable getCause() {
        return cause;
    }

    @Override
    public String toString() {
        return "TaskResult{" + status + (reason != null ? ", reason='" + reason + "'" : "") + "}";
    }
}
