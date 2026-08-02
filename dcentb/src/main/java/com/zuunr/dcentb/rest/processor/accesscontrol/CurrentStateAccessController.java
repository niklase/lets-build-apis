package com.zuunr.dcentb.rest.processor.accesscontrol;

import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonArrayBuilder;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import com.zuunr.json.schema.validation.JsonSchemaValidator;

public class CurrentStateAccessController extends PreOperationAccessController {

    private static final JsonSchemaValidator jsonSchemaValidator = new JsonSchemaValidator();
    private static final JsonArray PATH_TO_PERMISSION_SCHEMAS = JsonArray.of("x-dcentb", "accessControl", "permissionSchemas");
    private static final JsonArray PATH_TO_REQUEST_IN_ONE_PERMISSION_SCHEMA = JsonArray.of("requestSchema", "properties", "request");

    private JsonObject permissionSchemas;

    public CurrentStateAccessController(JsonValue config) {
        super(config);
    }

    @Override
    public JsonObject process(JsonObject requestContext) {
        return super.process(requestContext);
    }
}