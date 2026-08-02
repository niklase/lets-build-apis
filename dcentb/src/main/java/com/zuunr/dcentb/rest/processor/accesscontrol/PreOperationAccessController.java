package com.zuunr.dcentb.rest.processor.accesscontrol;

import com.zuunr.dcentb.rest.processor.Processor;
import com.zuunr.dcentb.rest.util.CollectionNameProvider;
import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonArrayBuilder;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import com.zuunr.json.pointer.JsonPointer;
import com.zuunr.json.schema.JsonSchema;
import com.zuunr.json.schema.validation.JsonSchemaValidator;
import com.zuunr.json.schema.validation.OutputStructure;
import com.zuunr.json.util.ApiErrorCreator;

public abstract class PreOperationAccessController extends Processor {

    private static final JsonPointer OPERATION_PERMISSION_SCHEMAS_POINTER = JsonPointer.of("#/operation/x-dcentb/accessControl/permissionSchemas");
    private static final JsonSchemaValidator jsonSchemaValidator = new JsonSchemaValidator();

    private JsonObject config;

    private JsonObject permissionSchemas;
    private ResponseAccessController responseAccessController;
    private JsonObject permissionSchemasPerCollection;
    private PermissionSchemasProvider permissionSchemasProvider;
    private String collectionName;

    public PreOperationAccessController(JsonValue config) {
        this.config = config.getJsonObject();
        permissionSchemas = config
                .get("operation")
                .get("x-dcentb", JsonObject.EMPTY)
                .get("accessControl", JsonObject.EMPTY)
                .get("permissionSchemas", JsonObject.EMPTY).getJsonObject();

        responseAccessController = config.as(ResponseAccessController.class);
        collectionName = CollectionNameProvider.getCollectionName(config.getJsonObject());
        permissionSchemasPerCollection = config
                .get("x-dcentb", JsonObject.EMPTY)
                .get("collections", JsonObject.EMPTY)
                .get(collectionName, JsonObject.EMPTY)
                .get("permissions", JsonObject.EMPTY)
                .getJsonObject();

        permissionSchemasProvider = config.as(PermissionSchemasProvider.class);
    }

    private static final JsonPointer userPermissionsPointer = JsonPointer.of("/userPermissions/body/items");
    private static final JsonPointer userInfoPointer = JsonPointer.of("/userPermissions/body/userInfo");

    public JsonObject process(JsonObject requestContext) {

        JsonValue errorstatus = null;
        JsonArray permissions = requestContext.get("authenticatedUser", JsonObject.EMPTY).get("permissions", JsonArray.EMPTY).getJsonArray();

        JsonValue authenticatedDefault = permissionSchemas.get("AUTHENTICATED_DEFAULT");
        boolean authenticatedDefaultPermissionExist = authenticatedDefault != null;
        if (authenticatedDefaultPermissionExist) {
            permissions = permissions.add("AUTHENTICATED_DEFAULT");
        }

        JsonObject candidateErrorResult = JsonObject.EMPTY.put("message", "Invalid request");


        for (int i = 0; i < permissions.size(); i++) {
            String permission = permissions.get(i).getString();
            JsonObject permissionAndSchemas = permissionSchemas.get(permission, JsonObject.EMPTY).getJsonObject();
            JsonArray permissionSchemasPointer = OPERATION_PERMISSION_SCHEMAS_POINTER.asArray().add(permission);
            JsonArray requestSchemaPointer = permissionSchemasPointer.add("requestSchema");

            JsonValue requestSchemaOfPermission = config.put("$ref", requestSchemaPointer.as(JsonPointer.class).getJsonPointerString()).jsonValue();
            JsonValue requestSchema = config.get(JsonPointer.of(requestSchemaPointer)); // TODO: This code could be optimized (cache per permission). Repeated creation of JSONPointer should be removed
            if (requestSchema == null) {
                requestSchemaOfPermission = JsonValue.FALSE;
            }


            JsonObject result = checkPermission(
                    requestSchemaOfPermission,
                    requestContext);

            if (result.get("valid", JsonValue.FALSE).getBoolean()) {

                JsonArray responseSchemaPointer = permissionSchemasPointer.add("responseSchema");
                JsonValue responseSchema = config.get(responseSchemaPointer);
                if (responseSchema == null) {
                    responseSchema = JsonValue.FALSE;
                } else {
                    responseSchema = config.put("$ref", responseSchemaPointer.as(JsonPointer.class).getJsonPointerString().getString()).jsonValue();
                }
                return requestContext.put("responseFilterSchemaPointer", responseSchemaPointer);
            } else {
                errorstatus = JsonValue.of(403);
                // Filter the currentstate as if it was the response
                JsonObject currentState = requestContext.get("currentState", JsonValue.NULL).getJsonObject();
                JsonObject requestContextWithCurrentStateFiltered = requestContext;
                if (currentState != null) {

                    JsonObject readItemSchema = config
                            .put("$ref", "#/$defs/Temp_Schema")
                            .put(JsonArray.of("$defs", "Temp_Schema"), JsonObject.EMPTY
                                    .put("properties", JsonObject.EMPTY
                                            .put("currentState", JsonObject.EMPTY
                                                    .put("$ref", JsonPointer.of(JsonArray.of("x-dcentb", "collections", collectionName, "permissions", permission, "readItem")).getJsonPointerString()))));
                    requestContextWithCurrentStateFiltered = jsonSchemaValidator.filter(requestContext.jsonValue(), readItemSchema.as(JsonSchema.class)).getJsonObject();
                    JsonValue currentStateFiltered = requestContextWithCurrentStateFiltered.get("currentState");
                    if (currentStateFiltered == null) {
                        errorstatus = JsonValue.of(404);
                    }

                    //requestContextWithCurrentStateFiltered = requestContext.put("currentState", currentStateFiltered);
                }

                JsonArray errors = ApiErrorCreator.ERROR_ARRAY_WITH_VIOLATIONS_ARRAY
                        .createErrors(
                                result,
                                requestContextWithCurrentStateFiltered.jsonValue(),
                                requestSchemaOfPermission.as(JsonSchema.class)
                        ).getJsonArray();

                errors = keepOnlyErrorsWithRejectedValue(errors);
                candidateErrorResult = candidateErrorResult.put("errors", errors);
            }
        }
        // TODO: Create "anyOf" where each item per permission.
        //       Make sure first validation only uses FLAG and then a full anyOf validation is done to give best feedback to user
        //throw new RuntimeException("Implement anyOf-schema based on all permissions of user");
        requestContext = requestContext.put("response", JsonObject.EMPTY.put("status", errorstatus).put("body", candidateErrorResult));
        return requestContext;
    }

    private JsonArray keepOnlyErrorsWithRejectedValue(JsonArray errors) {
        JsonArrayBuilder filteredErrors = JsonArray.EMPTY.builder();
        for (int i = 0; i < errors.size(); i++) {
            JsonObject errorElement = errors.get(i).getJsonObject();
            if (errorElement.get("rejectedValue") != null) {
                filteredErrors.add(errors.get(i));
            }
        }
        return filteredErrors.build();
    }

    private JsonObject checkPermission(JsonValue schemaOfPermission, JsonObject exchange) {
        JsonObject result = jsonSchemaValidator.validate(exchange.jsonValue(), schemaOfPermission, OutputStructure.DETAILED);
        return result;
    }
}
