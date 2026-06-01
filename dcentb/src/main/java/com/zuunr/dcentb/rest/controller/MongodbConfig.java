package com.zuunr.dcentb.rest.controller;

import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;

public class MongodbConfig {

    private JsonObject config;

    public MongodbConfig(JsonValue config){
        this.config = config.getJsonObject();
    }

    public String getCollection(){
        return config.get("collection").getString();
    }
}
