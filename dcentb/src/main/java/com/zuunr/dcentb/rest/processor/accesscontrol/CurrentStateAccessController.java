package com.zuunr.dcentb.rest.processor.accesscontrol;

import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonArrayBuilder;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import com.zuunr.json.schema.validation.JsonSchemaValidator;

public class CurrentStateAccessController extends PreOperationAccessController {



    private static final JsonSchemaValidator jsonSchemaValidator = new JsonSchemaValidator();
    private static final JsonArray PATH_TO_PERMISSION_SCHEMAS = JsonArray.of("x-dcentb", "accessControl", "permissionSchemas");
    private static final JsonArray PATH_TO_REQUEST_IN_ONE_PERMISSION_SCHEMA = JsonArray.of("requestSchema", "properties", "request");


    private JsonObject permissionSchemas;

    public CurrentStateAccessController(JsonValue config) {
        super(removeCurrentStateFromPermissionSchemas(config.getJsonObject()).jsonValue());

    }

    private static JsonObject removeCurrentStateFromPermissionSchemas(JsonObject config) {
        JsonArray permissionSchemas = config.get(PATH_TO_PERMISSION_SCHEMAS, JsonArray.EMPTY).getJsonArray();

        JsonArrayBuilder permissionSchemasBuilder = JsonArray.EMPTY.builder();
        for (int i = 0; i < permissionSchemas.size(); i++) {
            JsonObject permissionSchema = permissionSchemas.get(i).getJsonObject();
            permissionSchema = permissionSchema.remove(PATH_TO_REQUEST_IN_ONE_PERMISSION_SCHEMA);
            permissionSchemasBuilder.add(permissionSchema);
        }
        return config.put(PATH_TO_PERMISSION_SCHEMAS, permissionSchemasBuilder.build());
    }
}