package com.zuunr.dcentb.rest.processor.mongo;

import com.zuunr.dcentb.rest.processor.Processor;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;

public class MongoItemCreator implements Processor {

    private final String path;

    public MongoItemCreator(JsonValue config) {
        path = config.get("path").getString();

    }

    @Override
    public JsonObject process(JsonObject requestContext) {
        JsonObject restItem = requestContext.get("apiItem").getJsonObject();

        JsonObject meta = restItem.get("meta", JsonObject.EMPTY).getJsonObject();
        JsonObject mongoItem = restItem
                .put("_id", meta.get("id"))
                .put("meta", meta.remove("id"));

        requestContext = requestContext.put("mongoItem", mongoItem);

        return requestContext;
    }
}
