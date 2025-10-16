package com.zuunr.formbuilder;

import com.zuunr.json.JsonObject;

public interface ItemStateUpdater {
    public JsonObject mergeState(JsonObject itemState, JsonObject requestItem);

    public JsonObject expandState(JsonObject itemState);

}
