package com.zuunr.dcentb.rest.processor.accesscontrol;

import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonArrayBuilder;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import com.zuunr.json.schema.validation.JsonSchemaValidator;

public class RequestAccessController extends CurrentStateAccessController {

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
    private static final JsonArray PATH_TO_PERMISSION_SCHEMAS = JsonArray.of("x-dcentb", "accessControl", "permissionSchemas");
    private static final JsonArray PATH_TO_CURRENT_STATE_IN_ONE_PERMISSION_SCHEMA = JsonArray.of("requestSchema", "properties", "currentState");


    private JsonObject permissionSchemas;

    public RequestAccessController(JsonValue config) {
        super(removeCurrentStateFromPermissionSchemas(config.getJsonObject()).jsonValue());

    }

    private static JsonObject removeCurrentStateFromPermissionSchemas(JsonObject config) {
        JsonArray permissionSchemas = config.get(PATH_TO_PERMISSION_SCHEMAS, JsonArray.EMPTY).getJsonArray();

        JsonArrayBuilder permissionSchemasBuilder = JsonArray.EMPTY.builder();
        for (int i = 0; i < permissionSchemas.size(); i++) {
            JsonObject permissionSchema = permissionSchemas.get(i).getJsonObject();
            permissionSchema = permissionSchema.remove(PATH_TO_CURRENT_STATE_IN_ONE_PERMISSION_SCHEMA);
            permissionSchemasBuilder.add(permissionSchema);
        }
        return config.put(PATH_TO_PERMISSION_SCHEMAS, permissionSchemasBuilder.build());
    }
}