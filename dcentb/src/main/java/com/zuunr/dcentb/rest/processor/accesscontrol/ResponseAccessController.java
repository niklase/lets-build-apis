package com.zuunr.dcentb.rest.processor.accesscontrol;

import com.zuunr.dcentb.rest.processor.Processor;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import com.zuunr.json.pointer.JsonPointer;
import com.zuunr.json.schema.JsonSchema;
import com.zuunr.json.schema.validation.JsonSchemaValidator;

public class ResponseAccessController extends Processor {

    private JsonObject config;

    public ResponseAccessController(JsonValue config) {
        super(config);
        this.config = config.getJsonObject();
    }

    @Override
    public JsonObject process(JsonObject requestContext) {

        JsonObject response = requestContext.get(RESPONSE, JsonObject.EMPTY).getJsonObject();

        switch (response.get("status").getInteger()) {
            case 400, 401, 403: {
                break;
            }
            default: {
                requestContext = filterResponse(requestContext);
            }
        }
        return requestContext;
    }

    private JsonObject filterResponse(JsonObject requestContext) {

        JsonObject response = requestContext.get(RESPONSE, JsonObject.EMPTY).getJsonObject();
        JsonPointer responseFilterSchemaPointer = requestContext.get("responseFilterSchemaPointer").as(JsonPointer.class);
        JsonObject responseFilterSchema = config.put("$ref", responseFilterSchemaPointer.getJsonPointerString());


        JsonValue status = response.get("status");

        JsonValue body = response.get("body");
        JsonSchemaValidator validator = new JsonSchemaValidator();
        JsonValue filteredRequestContext = validator.filter(requestContext.jsonValue(), responseFilterSchema.as(JsonSchema.class));
        filteredRequestContext = filteredRequestContext == null ? JsonObject.EMPTY.jsonValue() : filteredRequestContext;
        JsonObject filteredResponse = filteredRequestContext.get(RESPONSE, JsonObject.EMPTY).getJsonObject();

        filteredResponse = filteredResponse.put("status", status); // status should never be filtered out
        if (status.getInteger() == 400) {
            filteredResponse = filteredResponse.put("body", body);
        }

        JsonValue filteredResponseBody = filteredResponse.get("body");

        if (filteredResponseBody == null) {
            int statusOfNoBody = status.getInteger() == 200
                    ? 404
                    : status.getInteger();
            requestContext = requestContext
                    .put(RESPONSE, filteredResponse
                            .remove("body")
                            .put("status", statusOfNoBody));
        } else {
            requestContext = requestContext
                    .put(RESPONSE, filteredResponse
                            .put("body", filteredResponseBody));
        }
        return requestContext;
    }
}