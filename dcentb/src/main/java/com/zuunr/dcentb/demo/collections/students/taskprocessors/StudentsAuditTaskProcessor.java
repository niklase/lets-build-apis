package com.zuunr.dcentb.demo.collections.students.taskprocessors;

import com.zuunr.dcentb.async.taskprocessing.TaskProcessor;
import com.zuunr.dcentb.async.taskprocessing.TaskResult;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reference example for docs/async-tasks-processing.md: a direct-attach (single-subscriber, no
 * fan-out) TaskProcessor wired to the "students" topic in demo.openapi.json. Logs every write to the
 * students collection — a minimal, honest demonstration that the async pipeline (leader election →
 * change stream → topic routing → this class) is actually running in the demo app, not just built
 * and unit-tested in isolation.
 */
public class StudentsAuditTaskProcessor implements TaskProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(StudentsAuditTaskProcessor.class);

    public StudentsAuditTaskProcessor(JsonValue config) {
        // no configuration needed yet; (JsonValue) constructor required by TaskProcessorResolver's
        // reflective-construction fallback (see docs/async-tasks-processing.md §14)
    }

    @Override
    public TaskResult process(JsonObject event) {
        String operationType = event.get("operationType").getString();
        JsonValue documentKey = event.get("documentKey");
        LOG.info("[students-audit] {} documentKey={}", operationType, documentKey);
        return TaskResult.success();
    }
}
