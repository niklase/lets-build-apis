package com.zuunr.dcentb.itemdecoratortest.collections.springmanaged;

import com.zuunr.dcentb.rest.processor.Processor;
import com.zuunr.json.JsonObject;

/**
 * Deliberately has NO (JsonValue) constructor - only a Spring-managed instance (with
 * "greeting" injected by the test's ApplicationContext) can ever construct this,
 * proving ItemDecoratorProcessor's Spring-bean resolution path is what's used, not
 * reflection.
 */
public class ItemDecorator extends Processor {

    private final String greeting;

    public ItemDecorator(String greeting) {
        this.greeting = greeting;
    }

    @Override
    public JsonObject process(JsonObject requestContext) {
        JsonObject itemState = requestContext.get("itemState", JsonObject.EMPTY).getJsonObject();
        return requestContext.put("itemState", itemState.put("greeting", greeting));
    }
}
