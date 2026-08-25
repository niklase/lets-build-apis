package com.zuunr.dcentb.rest.processor.apimodel;

import com.zuunr.json.JsonValue;

public class NewStateItemDecorator extends ItemDecoratorProcessor {

    public NewStateItemDecorator(JsonValue config) {
        super(config);
    }

    @Override
    protected String stateKey() {
        return "newState";
    }
}
