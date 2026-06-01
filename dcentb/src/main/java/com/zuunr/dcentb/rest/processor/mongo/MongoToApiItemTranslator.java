package com.zuunr.dcentb.rest.processor.mongo;

import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;

public final class MongoToApiItemTranslator {

    public static JsonObject getRestItem(JsonObject mongoItem) {
        JsonValue itemId = null;
        JsonValue _id = mongoItem.get("_id");
        if (_id.isString()) {
            itemId = _id;
        } else if (_id.get("ObjectId") != null) {
            itemId = _id.get("ObjectId");
        }

        mongoItem = mongoItem.put(JsonArray.of("meta", "id"), itemId).remove("_id");
        return mongoItem;
    }

}
