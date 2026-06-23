package com.zuunr.dcentb.rest.processor.accesscontrol;

import com.zuunr.dcentb.rest.processor.Processor;
import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonObjectBuilder;
import com.zuunr.json.JsonValue;
import com.zuunr.json.pointer.JsonPointer;
import com.zuunr.json.schema.JsonSchema;
import com.zuunr.json.schema.validation.JsonSchemaValidator;
import com.zuunr.json.schema.validation.OutputStructure;
import com.zuunr.json.util.ApiErrorCreator;

public class RequestAccessController implements Processor {

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

    public RequestAccessController(JsonValue me) {
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
    }


    private static final JsonPointer userPermissionsPointer = JsonPointer.of("/userPermissions/body/items");
    private static final JsonPointer userInfoPointer = JsonPointer.of("/userPermissions/body/userInfo");

    public JsonObject process(JsonObject requestContext) {

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
                    permission,
                    requestSchemaOfPermission,
                    requestContext);
            if (result.get("valid", JsonValue.FALSE).getBoolean()) {
                return requestContext.put("responseFilterSchema", permissionAndSchemas.get("responseSchema", JsonValue.FALSE));
            } else {
                JsonValue errors = ApiErrorCreator.ERROR_ARRAY_WITH_VIOLATIONS_ARRAY.createErrors(result, requestContext.jsonValue(), requestSchemaOfPermission.as(JsonSchema.class));
                candidateErrorResult = candidateErrorResult.put("errors", errors);
            }
        }
        // TODO: Create "anyOf" where each item per permission.
        //       Make sure first validation only uses FLAG and then a full anyOf validation is done to give best feedback to user
        //throw new RuntimeException("Implement anyOf-schema based on all permissions of user");
        return requestContext.put("response", JsonObject.EMPTY.put("status", 403).put("body", candidateErrorResult));
    }

    private JsonObject checkPermission(String permission, JsonValue schemaOfPermission, JsonObject exchange) {
        JsonObject result = jsonSchemaValidator.validate(exchange.jsonValue(), schemaOfPermission, OutputStructure.DETAILED);
        return result;
    }

}
