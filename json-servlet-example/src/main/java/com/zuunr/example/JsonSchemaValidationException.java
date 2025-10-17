package com.zuunr.example;

import com.zuunr.json.JsonObject;
import com.zuunr.json.schema.JsonSchema;

/**
 * @author Niklas Eldberger
 */
public class JsonSchemaValidationException extends RuntimeException {

    private JsonObject validationError;
    private JsonSchema jsonSchema;

    public JsonSchemaValidationException(JsonObject validationError, JsonSchema jsonSchema) {
        super();
        this.validationError = validationError;
        this.jsonSchema = jsonSchema;
    }

    public JsonObject getValidationError() {
        return validationError;
    }
    public JsonSchema getJsonSchema() {
        return jsonSchema;
    }
}
