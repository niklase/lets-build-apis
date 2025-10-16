package com.zuunr.formbuilder.http;

import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonObjectMerger;
import com.zuunr.json.JsonValue;
import com.zuunr.json.merge.MergeStrategy;
import com.zuunr.json.pointer.JsonPointer;


public class ItemStateForger {

    private static final JsonObjectMerger jsonMerger = new JsonObjectMerger();
    private JsonObject config;

    public ItemStateForger(JsonObject config) {
        this.config = config;
    }

    public JsonObject forgeStates(JsonObject requestItem, JsonObject itemState) {
        JsonObject result = jsonMerger.merge(itemState, requestItem, MergeStrategy.NULL_AS_DELETE_AND_ARRAY_AS_ATOM);
        for (JsonValue pointerJsonValue: config.get("patchAsAtoms", JsonArray.EMPTY).getJsonArray()){
            JsonPointer pointer = pointerJsonValue.as(JsonPointer.class);
            JsonValue requestValue = requestItem.get(pointer);
            if (requestValue != null) {
                result = result.put(pointer, requestValue);
            }
        }
        return result;
    }
}