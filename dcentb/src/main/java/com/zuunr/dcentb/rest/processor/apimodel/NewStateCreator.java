package com.zuunr.dcentb.rest.processor.apimodel;

import com.zuunr.dcentb.rest.processor.Processor;
import com.zuunr.dcentb.rest.util.BackendTime;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonObjectMerger;
import com.zuunr.json.JsonValue;

import java.util.UUID;

public class NewStateCreator extends Processor {

    public static final JsonObjectMerger merger = new JsonObjectMerger();

    private final String path;
    private final String method;

    public NewStateCreator(JsonValue config) {
        super(config);
        path = config.get("path").getString();
        method = config.get("method").getString();
    }

    @Override
    public JsonObject process(JsonObject requestContext) {

        JsonObject request = requestContext.get("request").getJsonObject();
        JsonValue newState = request.get("body");
        String itemId = request.get("pathParameters", JsonObject.EMPTY).get("id", JsonValue.NULL).getString();

        JsonValue currentState = requestContext.get("currentState", JsonValue.NULL);

        switch (method.toUpperCase()) {
            case "DELETE": {
                requestContext = requestContext.put("newState", JsonValue.NULL);
                break;
            }
            case "PUT": {
                newState = withFreshMeta(newState, itemId, request.get("uri"));
                requestContext = requestContext
                        .put("newState", newState);
                break;
            }
            case "POST": {
                itemId = UUID.randomUUID().toString().replace("-", "");
                newState = withFreshMeta(newState, itemId, JsonValue.of(path + "/" + itemId));
                requestContext = requestContext
                        .put("newState", newState);
                break;
            }
            case "PATCH": {
                itemId = request.get("pathParameters").get("id").getString();
                newState = merger.merge(currentState.getJsonObject(), newState.getJsonObject()).jsonValue();
                JsonValue updatedAt = JsonValue.of(BackendTime.dateTimeNow());

                JsonObject newStateMeta = newState.get("meta", JsonObject.EMPTY).getJsonObject();
                newStateMeta = newStateMeta.put("id", itemId).put("updatedAt", updatedAt);
                newState = newState.getJsonObject().put("meta", newStateMeta).jsonValue();
                requestContext = requestContext.put("newState", newState);
                break;
            }
            default:
                throw new IllegalStateException("Unexpected value: " + method.toUpperCase());
        }
        return requestContext.put("itemId", itemId);
    }

    /**
     * The "brand new item" meta block PUT (create) and POST both need — identical except for
     * where {@code id}/{@code href} come from (PUT: the path's {id}; POST: a freshly minted
     * one). Previously duplicated inline in both branches; consolidated here so there's exactly
     * one place that decides what "freshly created" metadata looks like.
     */
    private static JsonValue withFreshMeta(JsonValue body, String itemId, JsonValue href) {
        JsonValue createdAt = JsonValue.of(BackendTime.dateTimeNow());
        return body.getJsonObject()
                .put("meta", JsonObject.EMPTY
                        .put("createdAt", createdAt)
                        .put("updatedAt", createdAt)
                        .put("id", itemId)
                        .put("href", href)
                        .put("etag", UUID.randomUUID().toString().replace("-", ""))).jsonValue();
    }
}
