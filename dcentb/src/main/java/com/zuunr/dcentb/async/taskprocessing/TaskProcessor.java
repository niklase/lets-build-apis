package com.zuunr.dcentb.async.taskprocessing;

import com.zuunr.json.JsonObject;

/**
 * The async-processing equivalent of {@code com.zuunr.dcentb.rest.processor.Processor} — an
 * idempotent unit of work, but for a change stream event instead of an HTTP request/response. Not a
 * subclass of {@code Processor}: there is no request/response requestContext here, just the raw
 * event (the same JSON shape {@code ChangeStreamListener} hands to its eventHandler — see
 * docs/change-stream-listener.md — for a direct-attach subscriber; the fan-out publisher's stored
 * {@code payload} for a fan-out subscriber, see docs/async-tasks-processing.md §9.1).
 *
 * <p><b>Must be idempotent.</b> {@link TopicRouter} delivers at-least-once: a crash, a lost fencing
 * check, or an explicit {@link TaskResult#retry} can all cause the same event to be handed to
 * {@link #process} more than once.
 */
public interface TaskProcessor {

    TaskResult process(JsonObject event);
}
