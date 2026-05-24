package com.zuunr.dcentb.rest.processor.accesscontrol;

import static com.zuunr.dcentb.rest.processor.RequestContextConstants.*;

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
        JsonSchema responseFilterSchema = requestContext.get("responseFilterSchema", false).as(JsonSchema.class);
        JsonValue body = requestContext.get(RESPONSE, JsonObject.EMPTY).getJsonObject().get("body");
        JsonSchemaValidator validator = new JsonSchemaValidator();
        JsonValue filteredRequestContext = validator.filter(requestContext.jsonValue(), responseFilterSchema);
        JsonObject response = filteredRequestContext.get(RESPONSE, JsonObject.EMPTY).getJsonObject();
        JsonValue filteredResponseBody = response.get("body");

        requestContext = filteredResponseBody == null
                ? requestContext.put(RESPONSE, response.remove("body"))
                : requestContext.put(RESPONSE, response.put("body", filteredResponseBody));

        return requestContext;
    }
}
