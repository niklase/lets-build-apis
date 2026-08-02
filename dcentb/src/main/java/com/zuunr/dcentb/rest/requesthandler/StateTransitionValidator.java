package com.zuunr.dcentb.rest.requesthandler;

import com.zuunr.dcentb.rest.processor.Processor;
import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import com.zuunr.json.schema.JsonSchema;
import com.zuunr.json.schema.validation.JsonSchemaValidator;
import com.zuunr.json.schema.validation.OutputStructure;
import com.zuunr.json.util.ApiErrorCreator;

public class StateTransitionValidator extends Processor {

    private JsonObject config;

    private JsonSchema stateTransitionSchema;
    private static final JsonSchemaValidator jsonSchemaValidator = new JsonSchemaValidator();

    public StateTransitionValidator(JsonValue jsonValue) {
        config = jsonValue.getJsonObject();

        String method = config.get("method").toString();

        stateTransitionSchema = config
                .get("operation", JsonObject.EMPTY)
                .get(X_DCENTB, JsonObject.EMPTY)
                .get("stateTransitionSchema", JsonObject.EMPTY).getJsonObject()
                .put(JsonArray.of(X_DCENTB, "collections"), config.get(X_DCENTB, JsonObject.EMPTY).getJsonObject().get("collections", JsonObject.EMPTY))
                .as(JsonSchema.class);
    }

    @Override
    public JsonObject process(JsonObject requestContext) {

        JsonValue instance = JsonObject.EMPTY
                .put("currentState", requestContext.get("currentState"))
                .put("newState", requestContext.get("newState")).jsonValue();

        JsonObject result = jsonSchemaValidator.validate(instance, stateTransitionSchema, OutputStructure.DETAILED);
        if (JsonValue.FALSE.equals(result.get("valid"))) {
            return requestContext
                    .put("response", JsonObject.EMPTY
                            .put("status", 409)
                            .put("body", ApiErrorCreator.ERROR_ARRAY_WITH_VIOLATIONS_ARRAY
                                    .createErrorsAndSchemaObject(result, instance, stateTransitionSchema)));
        }

        return requestContext;
    }
}
