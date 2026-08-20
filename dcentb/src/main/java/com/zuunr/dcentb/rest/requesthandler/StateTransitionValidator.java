package com.zuunr.dcentb.rest.requesthandler;

import com.zuunr.dcentb.rest.controller.RequestHandlerConfig;
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
    private RequestHandlerConfig requestHandlerConfig;

    private JsonSchema stateTransitionSchema;
    private static final JsonSchemaValidator jsonSchemaValidator = new JsonSchemaValidator();

    public StateTransitionValidator(JsonValue config) {
        super(config);
        this.config = config.getJsonObject();

        requestHandlerConfig = this.config.as(RequestHandlerConfig.class);

        stateTransitionSchema = this.config
                .get("operation", JsonObject.EMPTY)
                .get(X_DCENTB, JsonObject.EMPTY)
                .get("stateTransitionSchema", JsonObject.EMPTY).getJsonObject()
                .put(JsonArray.of(X_DCENTB, "collections"), this.config.get(X_DCENTB, JsonObject.EMPTY).getJsonObject().get("collections", JsonObject.EMPTY))
                .as(JsonSchema.class);
    }

    @Override
    public JsonObject process(JsonObject requestContext) {

        JsonValue currentState = requestContext.get("currentState");
        JsonValue newState = requestContext.get("newState");

        JsonValue instance = JsonObject.EMPTY
                .put("currentState", currentState)
                .put("newState", newState).jsonValue();

        // does resource exist? → if no, create, 201. If yes, does the body match the stored representation? → if yes, 200 (no-op); if no, 409.

        JsonSchema schema = stateTransitionSchema;
        JsonObject result;
        if (requestHandlerConfig.isMethod("PUT") && !currentState.isNull()) {

            JsonObject newStateNoMeta = newState.getJsonObject().remove("meta");
            JsonObject currentStateNoMeta = currentState.getJsonObject().remove("meta");
            if (currentStateNoMeta.equals(newStateNoMeta)) {
                result = JsonObject.EMPTY.put("valid", true);
            } else {
                schema = JsonObject.EMPTY
                        .put("properties", JsonObject.EMPTY
                                .put("currentState", JsonObject.EMPTY
                                        .put("const", JsonValue.NULL))).as(JsonSchema.class);
                result = jsonSchemaValidator.validate(
                        instance,
                        schema,
                        OutputStructure.DETAILED);
            }
        } else {
            result = jsonSchemaValidator.validate(instance, schema, OutputStructure.DETAILED);
        }
        if (JsonValue.FALSE.equals(result.get("valid"))) {
            return requestContext
                    .put("response", JsonObject.EMPTY
                            .put("status", 409)
                            .put("body", ApiErrorCreator.ERROR_ARRAY_WITH_VIOLATIONS_ARRAY
                                    .createErrorsAndSchemaObject(result, instance, schema)));
        }
        return requestContext;
    }
}
