package com.zuunr.dcentb.rest.processor;

import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;

import java.util.UUID;

public class RestItemUtil {

    private static final JsonArray ETAG = JsonArray.of("meta", "etag");

    public static final JsonObject createPersistentItem(JsonObject item) {
        item.put(ETAG, UUID.randomUUID().toString().replace("-", ""));
        return item;
    }
}
