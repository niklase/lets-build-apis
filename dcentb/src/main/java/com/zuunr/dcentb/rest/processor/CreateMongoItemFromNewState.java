package com.zuunr.dcentb.rest.processor;

import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;

public class CreateMongoItemFromNewState extends Processor {

    private final String path;

    public CreateMongoItemFromNewState(JsonValue config) {
        super(config);
        path = config.get("path").getString();
    }

    @Override
    public JsonObject process(JsonObject requestContext) {
        JsonObject restItem = requestContext.get("newState").getJsonObject();

        JsonObject meta = restItem.get("meta", JsonObject.EMPTY).getJsonObject();
        JsonObject mongoItem = restItem
                .put("_id", meta.get("id"))
                .put("meta", meta.remove("id"));

        requestContext = requestContext.put("mongoItem", mongoItem);

        return requestContext;
    }
}
