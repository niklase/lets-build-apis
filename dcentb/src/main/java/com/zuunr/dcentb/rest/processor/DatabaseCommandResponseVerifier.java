package com.zuunr.dcentb.rest.processor;

import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;

public class DatabaseCommandResponseVerifier extends Processor {

    public DatabaseCommandResponseVerifier(JsonValue config) {
        super(config);
    }

    @Override
    public JsonObject process(JsonObject requestContext) {
        JsonObject mongoCommand = requestContext.get("mongoCommand", JsonValue.NULL).getJsonObject();
        if (mongoCommand != null) {
            JsonObject mongoResult = requestContext.get("mongoResult", JsonValue.NULL).getJsonObject();
            if (!mongoResult.get("ok").getInteger().equals(1)) {
                return requestContext.put("response", JsonObject.EMPTY.put("status", 500));
            } else {
                JsonArray writeErrors = mongoResult.get("writeErrors", JsonValue.NULL).getJsonArray();
                if (writeErrors != null && !writeErrors.isEmpty()) {
                    int code = writeErrors.get(0).get("code").getInteger();
                    int status = code == 11000 ? 409 : 500;
                    return requestContext.put("response", JsonObject.EMPTY.put("status", status));
                }
                // A delete command matching zero documents (DatabaseCUDItemCommandCreator's
                // "_id AND meta.etag" precondition) means the item was modified or deleted by
                // someone else after this request read it as currentState - an optimistic-
                // concurrency conflict, not a no-op. CurrentStateFromDatabaseApplier already
                // returned 404 earlier in this same request if the item never existed at all,
                // so by the time we're here "matched nothing" can only mean "matched nothing
                // anymore" - 409, the same status a stale-etag PATCH gets from the writeErrors
                // branch above.
                JsonObject deleteCommand = mongoCommand.get("delete", JsonValue.NULL).getJsonObject();
                if (deleteCommand != null) {
                    Integer deletedCount = mongoResult.get("n", JsonValue.NULL).getInteger();
                    if (deletedCount != null && deletedCount == 0) {
                        return requestContext.put("response", JsonObject.EMPTY.put("status", 409));
                    }
                }
            }
        }

        return requestContext;
    }
}
