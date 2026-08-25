package com.zuunr.dcentb.rest.processor.apimodel;

import com.zuunr.json.JsonValue;

public class CurrentStateItemDecorator extends ItemDecoratorProcessor {

    public CurrentStateItemDecorator(JsonValue config) {
        super(config);
    }

    @Override
    protected String stateKey() {
        return "currentState";
    }
}
