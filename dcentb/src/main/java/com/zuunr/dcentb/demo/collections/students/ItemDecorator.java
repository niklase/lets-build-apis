package com.zuunr.dcentb.demo.collections.students;

import com.zuunr.dcentb.rest.processor.Processor;
import com.zuunr.dcentb.rest.processor.mongo.MongoJsonDBHandle;
import com.zuunr.dcentb.rest.processor.mongo.MongoToApiItemTranslator;
import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import com.zuunr.mongodb.MongoJsonDB;

/**
 * Demo-only illustration of the ItemDecorator convention: computes "attendanceStatus"
 * from "attendancePercent" on whichever state it's handed via "itemState". Picked up
 * automatically by CurrentStateItemDecorator/NewStateItemDecorator because
 * demo.openapi.json sets x-dcentb.decoratorBasePackage=com.zuunr.dcentb.demo and this
 * class sits at <that package>.collections.students.ItemDecorator.
 *
 * Also embeds "teacher" - the full teachers/{id} item referenced by this student's
 * "teacherId" - so the students stateTransitionSchema can require newState.teacher.status
 * to enforce, declaratively, that the referenced teacher genuinely exists before a student
 * write is accepted (rather than as a database foreign-key constraint).
 *
 * The teacher lookup goes straight to MongoJsonDB (the same escape hatch
 * ApiKeyAuthenticator already uses), not through the REST API via SystemApiClient.
 * That's a deliberate, narrower choice than "always go through the API":
 * a plain by-id read of a collection with no ItemDecorator or write-side validation of its
 * own returns identical content either way, and this class - unlike a TaskProcessor - is
 * resolved reflectively per operation (ItemDecoratorProcessor.resolveDelegate) and must
 * keep working in test harnesses that construct RequestHandlerProvider directly without
 * ever booting Spring (e.g. ControllerIT), where no SystemApiClient bean exists to inject.
 * ClassSummarySyncTaskProcessor - which writes, and only ever runs inside a real
 * Spring-booted app - is where "via the REST API" actually matters, and does use
 * SystemApiClient. See docs/async-tasks-processing.md.
 */
public class ItemDecorator extends Processor {

    private final MongoJsonDB mongoJsonDB;

    public ItemDecorator(JsonValue config) {
        super(config);
        this.mongoJsonDB = config.as(MongoJsonDBHandle.class).getMongoJsonDB();
    }

    @Override
    public JsonObject process(JsonObject requestContext) {

        JsonObject itemState = requestContext.get("itemState", JsonValue.NULL).getJsonObject();

        JsonValue attendancePercent = itemState.get("attendancePercent");
        if (attendancePercent != null && attendancePercent.isJsonNumber()) {
            String attendanceStatus = attendancePercent.getInteger() < 60 ? "AT_RISK" : "OK";
            itemState = itemState.put("attendanceStatus", attendanceStatus);
        }

        JsonValue email = itemState.get("email");
        if (email != null) {
            itemState = itemState.put("email", email.getString().toLowerCase());
        }

        JsonValue teacherId = itemState.get("teacherId");
        if (teacherId != null && teacherId.isString()) {
            JsonObject teacher = findTeacher(teacherId.getString());
            if (teacher != null) {
                itemState = itemState.put("teacher", teacher.jsonValue());
            } else {
                itemState = itemState.remove("teacher");
            }
        }

        return requestContext.put("itemState", itemState);
    }

    private JsonObject findTeacher(String teacherId) {
        JsonObject findCommand = JsonObject.EMPTY.put("find", JsonObject.EMPTY
                .put("collection", "teachers")
                .put("filter", JsonObject.EMPTY
                        .put("_id", JsonArray.of(JsonObject.EMPTY.put("$eq", teacherId))))
                .put("limit", 1));

        JsonObject result = mongoJsonDB.runCommand(findCommand);
        JsonArray firstBatch = result.get("cursor", JsonObject.EMPTY).get("firstBatch", JsonArray.EMPTY).getJsonArray();

        if (firstBatch.isEmpty()) {
            return null;
        }

        return MongoToApiItemTranslator.getRestItem(firstBatch.get(0).getJsonObject());
    }
}
