package com.zuunr.dcentb.rest.processor;

import com.zuunr.api.openapi.OAS3Deserializer;
import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonObjectMerger;
import com.zuunr.json.JsonValue;

public class OASRequestDeserializer extends Processor {

    private static final JsonObjectMerger MERGER = new JsonObjectMerger();
    private JsonValue config;

    public OASRequestDeserializer(JsonValue config) {
        super(config);
        this.config = config;
    }

    @Override
    public JsonObject process(JsonObject requestContext) {

        JsonObject result = OAS3Deserializer.deserializeRequest(requestContext, config.getJsonObject(), JsonArray.of("operation"));
        if (result.get("ok").getBoolean()) {
            JsonObject request = MERGER.merge(requestContext.get(REQUEST).getJsonObject(), result.get(REQUEST).getJsonObject());
            return requestContext.put(REQUEST, request);
        } else {
            return requestContext
                    .put(RESPONSE, JsonObject.EMPTY
                            .put("status", 400)
                            .put("body", JsonObject.EMPTY
                                    .put("errors", result.get("errors"))));
        }
    }
}
