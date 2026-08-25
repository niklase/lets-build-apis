package com.zuunr.dcentb.itemdecoratortest.collections.widgets;

import com.zuunr.dcentb.rest.processor.Processor;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;

public class ItemDecorator extends Processor {

    public ItemDecorator(JsonValue config) {
        super(config);
    }

    @Override
    public JsonObject process(JsonObject requestContext) {
        JsonObject itemState = requestContext.get("itemState", JsonObject.EMPTY).getJsonObject();
        return requestContext.put("itemState", itemState.put("decorated", true));
    }
}
