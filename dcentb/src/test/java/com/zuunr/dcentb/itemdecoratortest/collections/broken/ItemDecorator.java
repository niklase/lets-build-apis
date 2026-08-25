package com.zuunr.dcentb.itemdecoratortest.collections.broken;

import com.zuunr.dcentb.rest.processor.Processor;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;

/**
 * Deliberately missing the (JsonValue) constructor config.as(...) requires, to verify
 * ItemDecoratorProcessor lets that failure propagate instead of swallowing it.
 */
public class ItemDecorator extends Processor {

    public ItemDecorator() {
        super(JsonValue.NULL);
    }

    @Override
    public JsonObject process(JsonObject requestContext) {
        return requestContext;
    }
}
