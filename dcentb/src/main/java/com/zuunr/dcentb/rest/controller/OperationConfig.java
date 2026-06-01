package com.zuunr.dcentb.rest.controller;

import com.zuunr.json.JsonValue;

public class OperationConfig {
    private JsonValue operationConfig;

    public OperationConfig(JsonValue operationConfig) {
        this.operationConfig = operationConfig;
    }


    public DcentbConfig getXDcentb() {
        return operationConfig.get("x-dcentb").as(DcentbConfig.class);
    }
}
