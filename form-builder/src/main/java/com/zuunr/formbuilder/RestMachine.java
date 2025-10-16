package com.zuunr.formbuilder;

import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import com.zuunr.json.schema.JsonSchema;
import com.zuunr.json.schema.validation.JsonSchemaValidator;
import com.zuunr.json.schema.validation.OutputStructure;
import com.zuunr.json.util.ApiErrorCreator;
import com.zuunr.json.util.StringSplitter;

import java.net.URI;

public class RestMachine {

    public static final JsonArray ITEM_ID_POINTER = JsonArray.of("meta", "id");

    JsonSchemaValidator validator = new JsonSchemaValidator();

    private final ConfigRegistry configRegistry = new ConfigRegistry();
    private final Database database;
    private final ItemStateUpdater itemStateUpdater;

    public RestMachine() {
        this(new InMemDB(), new TestItemStateUpdater());
    }


    public RestMachine(Database database, ItemStateUpdater itemStateUpdater) {
        this.database = database;
        this.itemStateUpdater = itemStateUpdater;
    }


    public JsonObject read(JsonObject request) {
        String method = request.get("method").getString().toUpperCase();
        URI uri = URI.create(request.get("uri").getString());

        boolean isRead = "GET".equals(method);
        JsonObject config = configRegistry.getConfig(request);
        JsonObject item = database.readItem(getCollectionNameOfItemUri(uri), getItemId(uri));
        return JsonObject.EMPTY.put("status", 200).put("body", item);
    }


    public JsonObject write(JsonObject request) {

        String method = request.get("method").getString().toUpperCase();
        URI uri = URI.create(request.get("uri").getString());

        String collectionName;

        boolean isCreate = false;
        boolean isUpdate = false;
        boolean isDelete = false;

        if ("POST".equals(method)) {
            isCreate = true;
            collectionName = request.get("uri").getString().substring(URI.create(request.get("uri").getString()).getPath().lastIndexOf('/') + 1);
        } else if ("PATCH".equals(method)) {
            isUpdate = true;
            collectionName = getCollectionNameOfItemUri(uri);

        } else if ("DELETE".equals(method)) {
            isDelete = true;
            collectionName = getCollectionNameOfItemUri(uri);

        } else {
            return JsonObject.EMPTY.put("status", 405);
        }

        JsonObject config = configRegistry.getConfig(request);

        JsonObject exchange = JsonObject.EMPTY.put("request", request);
        JsonObject errorResponse = validateRequest(exchange, config);
        if (errorResponse != null) {
            return errorResponse;
        }

        final JsonObject currentState;

        if (isUpdate || isDelete) {
            currentState = database.readItem(collectionName, getItemId(uri));
            if (currentState == null) {
                return JsonObject.EMPTY.put("status", 404);
            }
        } else {
            currentState = null;
        }

        final JsonObject newState;
        if (isCreate) {
            newState = request.get("body").getJsonObject();
        } else if (isUpdate) {
            newState = itemStateUpdater.mergeState(currentState, request.get("body").getJsonObject().remove("meta"));
        } else {
            newState = null;
        }

        JsonValue expandedNewState = newState == null ? JsonValue.NULL : itemStateUpdater.expandState(newState).jsonValue();
        JsonValue expandedCurrentState = currentState == null ? JsonValue.NULL : itemStateUpdater.expandState(currentState).jsonValue();

        JsonValue transitionSchema = config.get("transitionSchema", JsonValue.FALSE);

        JsonObject exchangeWithState = exchange
                .put("currentState", expandedCurrentState)
                .put("newState", expandedNewState);

        JsonObject validationResult = validator.validate(exchangeWithState.jsonValue(), transitionSchema, OutputStructure.DETAILED);
        if (!JsonValue.TRUE.equals(validationResult.get("valid"))) {
            return JsonObject.EMPTY.put("status", 409).put("body", validationResult);
        }

        if (isCreate) {

            JsonObject createdItem = database.createItem(collectionName, expandedNewState.getJsonObject());
            return JsonObject.EMPTY
                    .put("status", 201)
                    .put("headers", JsonObject.EMPTY
                            .put("location", JsonArray.of(URI.create(request.get("uri").getString()).getPath() + "/" + createdItem.get(JsonArray.of("meta", "id")).getString())))
                    .put("body", createdItem);
        } else if (isDelete) {
            database.deleteItem(collectionName, getItemId(uri));
            return JsonObject.EMPTY
                    .put("status", 204);
        } else if (isUpdate) {
            database.writeItem(collectionName, expandedNewState.getJsonObject());
            return JsonObject.EMPTY
                    .put("status", 200)
                    .put("body", newState);
        }
        return JsonObject.EMPTY.put("status", 500);
    }


    private String getCollectionNameOfItemUri(URI uri) {
        return StringSplitter.splitString(uri.getPath(), '/').allButLast().last().getString();
    }


    private String getItemId(URI uri) {
        return StringSplitter.splitString(uri.getPath(), '/').last().getString();
    }


    private JsonObject validateRequest(JsonObject request, JsonObject machineConfig) {

        // Validate request
        JsonValue requestSchema = machineConfig.get("requestSchema");

        JsonObject validationResult = validator.validate(request.jsonValue(), requestSchema, OutputStructure.DETAILED);
        if (!JsonValue.TRUE.equals(validationResult.get("valid"))) {
            return JsonObject.EMPTY.put("status", 400).put("body", ApiErrorCreator.ERROR_ARRAY_WITH_VIOLATIONS_ARRAY.createErrors(validationResult, request.jsonValue(), requestSchema.as(JsonSchema.class)));
        }
        return null;
    }
}