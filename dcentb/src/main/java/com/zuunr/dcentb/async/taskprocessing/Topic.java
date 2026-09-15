package com.zuunr.dcentb.async.taskprocessing;

import java.util.List;
import java.util.Objects;

/**
 * One entry from {@code x-dcentb.asyncProcessing.topics} (docs/async-tasks-processing.md §9.2).
 * Fan-out is derived from {@code subscribers.size()}, not a separate config flag, per §8.4 Q9: a
 * topic with exactly one subscriber attaches that {@link TaskProcessor} directly to the raw
 * source-collection change stream; more than one triggers the publish/fan-out path (a single shared
 * message-log write, then dispatch to every subscriber) instead of duplicating the payload once per
 * subscriber. See {@link TopicRouter}.
 */
public final class Topic {

    private final String name;
    private final String sourceCollection;
    private final List<Subscriber> subscribers;

    public Topic(String name, String sourceCollection, List<Subscriber> subscribers) {
        this.name = Objects.requireNonNull(name, "name");
        this.sourceCollection = Objects.requireNonNull(sourceCollection, "sourceCollection");
        if (subscribers == null || subscribers.isEmpty()) {
            throw new IllegalArgumentException("topic '" + name + "' must declare at least one subscriber");
        }
        this.subscribers = List.copyOf(subscribers);
    }

    public String getName() {
        return name;
    }

    public String getSourceCollection() {
        return sourceCollection;
    }

    public List<Subscriber> getSubscribers() {
        return subscribers;
    }

    public boolean isFanOut() {
        return subscribers.size() > 1;
    }
}
