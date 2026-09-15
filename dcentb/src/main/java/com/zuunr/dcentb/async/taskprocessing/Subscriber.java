package com.zuunr.dcentb.async.taskprocessing;

import java.util.Objects;

/** One named {@link TaskProcessor} within a {@link Topic}. */
public final class Subscriber {

    private final String name;
    private final TaskProcessor taskProcessor;

    public Subscriber(String name, TaskProcessor taskProcessor) {
        this.name = Objects.requireNonNull(name, "name");
        this.taskProcessor = Objects.requireNonNull(taskProcessor, "taskProcessor");
    }

    public String getName() {
        return name;
    }

    public TaskProcessor getTaskProcessor() {
        return taskProcessor;
    }
}
