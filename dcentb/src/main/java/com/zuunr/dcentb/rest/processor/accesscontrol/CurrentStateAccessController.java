package com.zuunr.dcentb.rest.processor.accesscontrol;

import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonArrayBuilder;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import com.zuunr.json.schema.validation.JsonSchemaValidator;

public class CurrentStateAccessController extends PreOperationAccessController {

    public CurrentStateAccessController(JsonValue config) {
        super(config);
    }

    @Override
    public JsonObject process(JsonObject requestContext) {
        return super.process(requestContext);
    }
}