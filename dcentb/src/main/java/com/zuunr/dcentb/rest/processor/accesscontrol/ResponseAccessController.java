package com.zuunr.dcentb.rest.processor.accesscontrol;

import com.zuunr.dcentb.rest.processor.Processor;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import com.zuunr.json.schema.JsonSchema;
import com.zuunr.json.schema.validation.JsonSchemaValidator;

public class ResponseAccessController implements Processor {

    public ResponseAccessController(JsonValue config) {

    }

    @Override
    public JsonObject process(JsonObject requestContext) {
        JsonObject response = requestContext.get(RESPONSE, JsonObject.EMPTY).getJsonObject();
        JsonSchema responseFilterSchema = requestContext.get("responseFilterSchema", false).as(JsonSchema.class);
        JsonValue status = response.get("status");
        JsonValue body = response.get("body");
        JsonSchemaValidator validator = new JsonSchemaValidator();
        JsonValue filteredRequestContext = validator.filter(requestContext.jsonValue(), responseFilterSchema);
        filteredRequestContext = filteredRequestContext == null ? JsonObject.EMPTY.jsonValue() : filteredRequestContext;
        JsonObject filteredResponse = filteredRequestContext.get(RESPONSE, JsonObject.EMPTY).getJsonObject();


        filteredResponse = filteredResponse.put("status", status); // status should never be filtered out
        if (status.getInteger() == 400) {
            filteredResponse = filteredResponse.put("body", body);
        }


            JsonValue filteredResponseBody = filteredResponse.get("body");

        requestContext = filteredResponseBody == null
                ? requestContext.put(RESPONSE, filteredResponse.remove("body"))
                : requestContext.put(RESPONSE, filteredResponse.put("body", filteredResponseBody));

        return requestContext;
    }
}
