package com.zuunr.dcentb.rest.controller;

import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;

public class DcentbConfig {

    private JsonObject dcentbConfig;

    public DcentbConfig(JsonValue dcentbConfig){
            this.dcentbConfig = dcentbConfig.getJsonObject();
    }

    public MongodbConfig getMongodb(){
        return dcentbConfig.get("mongodb", JsonObject.EMPTY).as(MongodbConfig.class);
    }
}
