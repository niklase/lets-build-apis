package com.zuunr.dcentb.rest.processor.accesscontrol;

import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import com.zuunr.json.schema.JsonSchema;

public class PermissionSchemasProvider {


    private JsonObject readAndWriteSchemas;

    public PermissionSchemasProvider(JsonValue openApiConfig) {

        readAndWriteSchemas = JsonObject.EMPTY;

        JsonObject perCollectionConfig = openApiConfig
                .get("x-dcentb", JsonObject.EMPTY)
                .get("collections", JsonObject.EMPTY).getJsonObject();

        JsonArray collectionNames = perCollectionConfig.keys();
        JsonArray collectionConfigs = perCollectionConfig.values();

        for (int i = 0; i < collectionNames.size(); i++) {
            String collectionName = collectionNames.get(i).asString();
            JsonObject collectionPermissions = collectionConfigs.get(i, JsonObject.EMPTY).getJsonObject().get("permissions", JsonObject.EMPTY).getJsonObject();

            JsonArray permissionNames = collectionPermissions.keys();
            JsonArray permissions = collectionPermissions.values();

            for (int j = 0; j < permissionNames.size(); j++) {
                String permissionName = permissionNames.get(j).getString();
                JsonObject permission = permissions.get(j).getJsonObject();

                JsonValue writeItem = permission.get("writeItemSchema", JsonValue.FALSE);
                JsonValue readItem = permission.get("readItemSchema", JsonValue.FALSE);
                JsonValue readCollection = permission.get("readCollectionSchema", JsonObject.EMPTY.put("type", "object").put("additionalProperties", false));
                readCollection = readCollection.put(JsonArray.of("properties", "items", "type"), "array");
                readCollection = readCollection.put(JsonArray.of("properties", "items", "items"), readItem);
                JsonObject updatedPermissions = JsonObject.EMPTY
                        .put("readCol", readCollection)
                        .put("readItem", readItem)
                        .put("writeItem", writeItem);

                readAndWriteSchemas = readAndWriteSchemas.put(JsonArray.of(collectionName, permissionName), updatedPermissions);
            }
        }

    }

    private JsonObject getSchemas(String collectionName, String permissionName) {
        return readAndWriteSchemas.get(collectionName, JsonObject.EMPTY).get(permissionName, JsonObject.EMPTY).getJsonObject();
    }

    public JsonSchema getReadItemSchema(String collectionName, String permissionName) {
        return getSchemas(collectionName, permissionName).get("readItem").as(JsonSchema.class);
    }

    public JsonSchema getReadCollectionSchema(String collectionName, String permissionName) {
        return getSchemas(collectionName, permissionName).get("readCol").as(JsonSchema.class);
    }

    public JsonSchema getWriteItemSchema(String collectionName, String permissionName) {
        return getSchemas(collectionName, permissionName).get("writeItem").as(JsonSchema.class);
    }


}
