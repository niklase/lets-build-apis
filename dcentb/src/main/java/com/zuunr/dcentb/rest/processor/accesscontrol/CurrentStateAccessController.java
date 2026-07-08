package com.zuunr.dcentb.rest.processor.accesscontrol;

import com.zuunr.dcentb.rest.processor.Processor;
import com.zuunr.json.*;
import com.zuunr.json.pointer.JsonPointer;
import com.zuunr.json.schema.JsonSchema;
import com.zuunr.json.schema.validation.JsonSchemaValidator;
import com.zuunr.json.schema.validation.OutputStructure;
import com.zuunr.json.util.ApiErrorCreator;

public class CurrentStateAccessController implements Processor {

    // background
    // {
    //  "$authz.validateRequest": {
    //    "userPermissions": "$/userPermissions",
    //    "userId": "$/userId",
    //    "exchange": "$/exchange",
    //    "errorBodyFormat": "WEB_API_ERRORS_OBJECT",
    //    "permissionSchemas": [
    //      {
    //        "permission": "productA",
    //        "requestSchema": {
    //          "properties": {
    //            "request": {
    //              "properties": {
    //                "body": {
    //                  "properties": {
    //                    "customerIdOfOrder": {
    //                      "equals": [
    //                        "/userInfo/customerIdOfUser"
    //                      ]
    //                    }
    //                  }
    //                }
    //              }
    //            }
    //          }
    //        }
    //      }
    //    ]
    //  }
    //}


    //    "permissionSchemas": [
    //      {
    //        "permission": "productA",
    //        "requestSchema": {
    //          "properties": {
    //            "request": {
    //              "properties": {
    //                "body": {
    //                  "properties": {
    //                    "customerIdOfOrder": {
    //                      "equals": [
    //                        "/userInfo/customerIdOfUser"
    //                      ]
    //                    }
    //                  }
    //                }
    //              }
    //            }
    //          }
    //        }
    //      }
    //    ]

    private static final JsonSchemaValidator jsonSchemaValidator = new JsonSchemaValidator();

    private JsonObject permissionSchemas;
    private ResponseAccessController responseAccessController;
    private JsonObject collectionPermissionSchemas;

    public CurrentStateAccessController(JsonValue me) {
        JsonObjectBuilder builder = JsonObject.EMPTY.builder();
        JsonArray permissionSchemasArray = me
                .get("operation")
                .get("x-dcentb", JsonObject.EMPTY)
                .get("accessControl", JsonObject.EMPTY)
                .get("permissionSchemas", JsonArray.EMPTY).getJsonArray();
        for (int i = 0; i < permissionSchemasArray.size(); i++) {
            JsonObject permissionSchema = permissionSchemasArray.get(i).getJsonObject();
            builder.put(permissionSchema.get("permission").getString(), permissionSchema);
        }
        permissionSchemas = builder.build();
        responseAccessController = me.as(ResponseAccessController.class);
        String collectionName = me.get("path").getString();
        collectionPermissionSchemas = me
                .get("x-dcentb", JsonObject.EMPTY)
                .get("collections", JsonObject.EMPTY)
                .get("students", JsonObject.EMPTY) // TODO: How get students?
                .get("permissions", JsonObject.EMPTY)
                .getJsonObject();
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
            JsonValue requestSchemaOfPermission = permissionAndSchemas.get("requestSchema");

            if (requestSchemaOfPermission == null) {
                continue;
            }
            JsonObject result = checkPermission(
                    requestSchemaOfPermission,
                    requestContext);
            if (result.get("valid", JsonValue.FALSE).getBoolean()) {
                return requestContext.put("responseFilterSchema", permissionAndSchemas.get("responseSchema", JsonValue.FALSE));
            } else {
                errorstatus = JsonValue.of(403);
                // Filter the currentstate as if it was the response
                JsonObject currentState = requestContext.get("currentState", JsonValue.NULL).getJsonObject();
                JsonObject requestContextWithCurrentStateFiltered = requestContext;
                if (currentState != null) {
                    JsonValue readItemSchema = collectionPermissionSchemas
                            .get(permission, JsonObject.EMPTY)
                            .get("readItemSchema", JsonValue.FALSE);
                    JsonObject contextWhereCurrentStateIsResponse = requestContext
                            .put("responseFilterSchema", readItemSchema)
                            .put("response", JsonObject.EMPTY
                                    .put("status", 200)
                                    .put("body", currentState));
                    JsonObject context = responseAccessController.process(contextWhereCurrentStateIsResponse);
                    JsonObject currentStateFiltered = context.get("response", JsonObject.EMPTY).getJsonObject().get("body", JsonObject.EMPTY).getJsonObject();
                    JsonValue errorStatusOfFictiveResponse = context.get("response", JsonObject.EMPTY).getJsonObject().get("status");
                    if (errorStatusOfFictiveResponse.getInteger() == 404) {
                        errorstatus = errorStatusOfFictiveResponse;
                    }
                    requestContextWithCurrentStateFiltered = requestContext.put("currentState", currentStateFiltered);
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
