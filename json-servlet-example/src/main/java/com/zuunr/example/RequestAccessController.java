package com.zuunr.example;

import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import com.zuunr.json.schema.JsonSchema;
import com.zuunr.json.schema.validation.JsonSchemaValidator;
import com.zuunr.json.schema.validation.OutputStructure;
import com.zuunr.json.util.ApiErrorCreator;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

public class RequestAccessController {

    private static final JsonSchemaValidator JSON_SCHEMA_VALIDATOR = new JsonSchemaValidator();
    private final PermissionSchemaProvider permissionSchemaProvider;

    public RequestAccessController(PermissionSchemaProvider permissionSchemaProvider) {
        this.permissionSchemaProvider = permissionSchemaProvider;
    }

    public JsonObject getAuthorizedExchange(JsonObject exchange) throws AuthorizationException {

        JsonObject request = exchange.get("request").getJsonObject();

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        JsonObject validationResult = null;
        JsonSchema requestSchema =  null;
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            requestSchema = permissionSchemaProvider.getRequestPermissionSchema(
                    request.get("path").getString(),
                    request.get("method").getString(),
                    authority.getAuthority()
            ).as(JsonSchema.class);
            validationResult = JSON_SCHEMA_VALIDATOR.validate(exchange.jsonValue(), requestSchema, OutputStructure.DETAILED);
            if (JsonValue.TRUE.equals(validationResult.get("valid"))) {
                return exchange;
            }
        }

        if (validationResult == null) {
            validationResult = JSON_SCHEMA_VALIDATOR.validate(exchange.jsonValue(), JsonObject.EMPTY.put("properties", JsonObject.EMPTY.put("request", false)).as(JsonSchema.class), OutputStructure.DETAILED);
        }

        JsonValue errors = ApiErrorCreator.ERROR_ARRAY_WITH_VIOLATIONS_ARRAY.createErrors(validationResult, exchange.jsonValue(), requestSchema);
        throw new AuthorizationException(JsonObject.EMPTY.put("errors", errors));
    }
}