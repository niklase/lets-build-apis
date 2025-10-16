package com.zuunr.formbuilder;

import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonObjectMerger;

public class TestItemStateUpdater implements ItemStateUpdater {

    private static final JsonObjectMerger JSON_OBJECT_MERGER = new JsonObjectMerger();

    public JsonObject expandState(JsonObject itemState) {
        return itemState;
    }

    public JsonObject mergeState(JsonObject itemState, JsonObject requestItem) {
        return JSON_OBJECT_MERGER.merge(itemState, requestItem);
    }
}
