package com.zuunr.dcentb.rest.processor.apimodel;

import com.zuunr.dcentb.rest.util.BackendTime;
import com.zuunr.dcentb.rest.processor.Processor;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;

import java.util.UUID;

public class ApiItemCreator implements Processor {

    private final String path;
    private final String method;

    public ApiItemCreator(JsonValue config) {
        path = config.get("path").getString();
        method = config.get("method").getString();
    }


    @Override
    public JsonObject process(JsonObject requestContext) {

        JsonObject apiItem = requestContext.get("request").get("body", JsonValue.NULL).getJsonObject();

        switch (method.toUpperCase()) {
            case "POST": {

                JsonValue createdAt = JsonValue.of(BackendTime.dateTimeNow());
                JsonValue updatedAt = createdAt;
                String id = UUID.randomUUID().toString().replace("-", "");
                apiItem = apiItem.put("meta", JsonObject.EMPTY
                        .put("createdAt", createdAt)
                        .put("updatedAt", updatedAt)
                        .put("id", id)
                        .put("href", path + "/" + id)
                        .put("etag", UUID.randomUUID().toString().replace("-", "")));
                break;
            }
            case "PATCH": {
                JsonValue updatedAt = JsonValue.of(BackendTime.dateTimeNow());
                apiItem.put("meta", JsonObject.EMPTY
                        .put("updatedAt", updatedAt));

            }
        }
        return requestContext.put("apiItem", apiItem);
    }
}
