package com.zuunr.dcentb.demo.collections.students.taskprocessors;

import com.zuunr.dcentb.async.taskprocessing.TaskProcessor;
import com.zuunr.dcentb.async.taskprocessing.TaskResult;
import com.zuunr.dcentb.rest.Response;
import com.zuunr.dcentb.rest.controller.SystemApiClient;
import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/**
 * Reference example for docs/async-tasks-processing.md: the second subscriber on the "students"
 * topic (alongside StudentsAuditTaskProcessor) - demonstrating a TaskProcessor that keeps
 * *another* document eventually consistent - a teacher's classSummary - purely via the REST API
 * (SystemApiClient), never touching MongoDB directly. Adding this second subscriber is also what
 * upgrades the "students" topic from direct-attach to fan-out-on-read (see TopicRouter) for the
 * first time in this demo.
 *
 * Idempotent by construction: every invocation recomputes the teacher's classSummary from
 * scratch (a fresh getCollection query over that teacher's current students) and PATCHes the
 * full, current result - never an increment/decrement - so redelivering the same event twice
 * (the at-least-once contract every TaskProcessor must tolerate) produces the same end state.
 *
 * Known, deliberate limitation: a MongoDB change stream never includes the deleted document on
 * a DELETE event (fullDocument is only populated for insert/replace, and - since
 * ChangeStreamListener requests FullDocument.UPDATE_LOOKUP - for update; never for delete). This
 * processor therefore cannot know which teacher a deleted student belonged to, and a deletion
 * leaves that teacher's classSummary stale until another event for the same teacher happens to
 * trigger a recompute. Fixable with fullDocumentBeforeChange (requires
 * changeStreamPreAndPostImages enabled on the students collection) - deliberately not enabled
 * here; flagged as a known gap rather than silently under-handled.
 */
@Component
public class ClassSummarySyncTaskProcessor implements TaskProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(ClassSummarySyncTaskProcessor.class);
    private static final int AT_RISK_ATTENDANCE_PERCENT_THRESHOLD = 60;

    private final SystemApiClient systemApiClient;

    public ClassSummarySyncTaskProcessor(SystemApiClient systemApiClient) {
        this.systemApiClient = systemApiClient;
    }

    @Override
    public TaskResult process(JsonObject event) {

        JsonValue fullDocument = event.get("fullDocument");
        if (fullDocument == null || !fullDocument.isJsonObject()) {
            // A delete, or an update whose lookup raced a later delete - see class Javadoc.
            return TaskResult.success();
        }

        JsonValue teacherIdValue = fullDocument.getJsonObject().get("teacherId");
        if (teacherIdValue == null || !teacherIdValue.isString()) {
            return TaskResult.success();
        }
        String teacherId = teacherIdValue.getString();

        Response<?> studentsResponse = systemApiClient.call("POST", "/students/getCollection", JsonObject.EMPTY
                .put("filter", JsonObject.EMPTY.put("teacherId", JsonObject.EMPTY.put("eq", teacherId)))
                .jsonValue());

        if (studentsResponse.getStatus() != 200) {
            return TaskResult.retry("POST /students/getCollection for teacherId=" + teacherId + " returned " + studentsResponse.getStatus());
        }

        JsonArray items = studentsResponse.getBody().getJsonObject().get("items", JsonArray.EMPTY).getJsonArray();

        int studentCount = items.size();
        int atRiskCount = 0;
        long attendanceSum = 0;
        for (int i = 0; i < items.size(); i++) {
            int attendancePercent = items.get(i).getJsonObject().get("attendancePercent", 0).getInteger();
            attendanceSum += attendancePercent;
            if (attendancePercent < AT_RISK_ATTENDANCE_PERCENT_THRESHOLD) {
                atRiskCount++;
            }
        }
        BigDecimal averageAttendancePercent = studentCount == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(attendanceSum).divide(BigDecimal.valueOf(studentCount), 2, RoundingMode.HALF_UP);

        JsonObject classSummary = JsonObject.EMPTY
                .put("studentCount", studentCount)
                .put("atRiskCount", atRiskCount)
                .put("averageAttendancePercent", averageAttendancePercent)
                .put("updatedAt", Instant.now().toString());

        Response<?> patchResponse = systemApiClient.call("PATCH", "/teachers/" + teacherId, JsonObject.EMPTY
                .put("classSummary", classSummary.jsonValue())
                .jsonValue());

        if (patchResponse.getStatus() == 404) {
            // The referenced teacher doesn't exist - students' stateTransitionSchema should
            // already have rejected the write that produced this event; nothing more to do.
            LOG.warn("[classSummarySync] teacherId={} not found while syncing classSummary - ignoring", teacherId);
            return TaskResult.success();
        }
        if (patchResponse.getStatus() != 200) {
            return TaskResult.retry("PATCH /teachers/" + teacherId + " returned " + patchResponse.getStatus());
        }

        LOG.info("[classSummarySync] teacherId={} studentCount={} atRiskCount={}", teacherId, studentCount, atRiskCount);
        return TaskResult.success();
    }
}
