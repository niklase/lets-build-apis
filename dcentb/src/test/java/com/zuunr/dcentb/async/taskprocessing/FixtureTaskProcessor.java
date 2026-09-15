package com.zuunr.dcentb.async.taskprocessing;

import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;

/** Fixture for {@link TaskProcessorResolverTest}'s reflective-construction fallback path. */
public class FixtureTaskProcessor implements TaskProcessor {

    private final JsonValue config;

    public FixtureTaskProcessor(JsonValue config) {
        this.config = config;
    }

    public JsonValue getConfig() {
        return config;
    }

    @Override
    public TaskResult process(JsonObject event) {
        return TaskResult.success();
    }
}
