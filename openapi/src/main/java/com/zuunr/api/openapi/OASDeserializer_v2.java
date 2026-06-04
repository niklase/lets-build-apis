package com.zuunr.api.openapi;

import com.zuunr.json.*;
import com.zuunr.json.schema.JsonSchema;
import com.zuunr.json.schema.Keywords;
import com.zuunr.json.schema.validation.JsonSchemaValidator;
import com.zuunr.json.schema.validation.OutputStructure;
import com.zuunr.json.util.ApiErrorCreator;
import com.zuunr.json.util.ApiErrorException;
import com.zuunr.json.util.StringSplitter;
import org.springframework.http.MediaType;

import java.io.InputStream;
import java.net.URI;

/**
 * Instance-based deserializer that operates on the full OpenAPI document so that
 * $ref pointers (e.g. #/components/schemas/…) can be resolved at validation time.
 *
 * Parameter schemas are pre-computed in the constructor; body validation uses the
 * path-based JsonSchemaValidator overload so the full document is available as the
 * $ref resolution root without rebuilding wrapper schemas on every request.
 */
public class OASDeserializer_v2 {

    private static final ApiErrorCreator API_ERROR_CREATOR = new ApiErrorCreator();
    private static final JsonSchemaValidator VALIDATOR = new JsonSchemaValidator();
    private static final JsonObjectFactory JSON_OBJECT_FACTORY = new JsonObjectFactory();

    private final JsonValue openApiDocument;
    private final JsonObject operation;
    private final JsonValue paramSchemaStrings;
    private final JsonValue paramSchema;
    private final boolean hasParameters;

    public OASDeserializer_v2(JsonObject openApiDocument, JsonObject operation) {
        this.openApiDocument = openApiDocument.jsonValue();
        JsonObject resolvedOperation = resolveRefsInParameters(operation, openApiDocument);
        this.operation = resolvedOperation;

        if (resolvedOperation.get("parameters") != null) {
            this.paramSchemaStrings = buildParameterSchema(resolvedOperation, true);
            this.paramSchema = buildParameterSchema(resolvedOperation, false);
            this.hasParameters = true;
        } else {
            this.paramSchemaStrings = null;
            this.paramSchema = null;
            this.hasParameters = false;
        }
    }

    // Static factory methods retained for callers that do not hold an instance
    public static JsonObject deserializeRequest(JsonObject exchangeWithRequest, JsonObject openApiDocument, JsonObject openApiOperationObject) {
        return new OASDeserializer_v2(openApiDocument, openApiOperationObject).deserializeRequest(exchangeWithRequest);
    }

    public static JsonObject deserializeRequest(JsonObject exchangeWithRequest, InputStream bodyInputStream, JsonObject openApiDocument, JsonObject openApiOperationObject) {
        return new OASDeserializer_v2(openApiDocument, openApiOperationObject).deserializeRequest(exchangeWithRequest, bodyInputStream);
    }

    public JsonObject deserializeRequest(JsonObject exchangeWithRequest) {
        return deserializeRequest(exchangeWithRequest, (InputStream) null);
    }

    public JsonObject deserializeRequest(JsonObject exchangeWithRequest, InputStream requestBodyInputStream) {
        JsonValue errors;
        JsonValue body;
        JsonObject deserializedReq = JsonObject.EMPTY;

        try {
            if (!hasParameters) {
                JsonValue query = exchangeWithRequest.get("request").get("uri").as(JsonUri.class).getQuery();
                JsonObject requestWithQuery = JsonObject.EMPTY.put("query", query);
                deserializedReq = deserializedReq.put(
                        "query",
                        OAS3Deserializer.parseQueryStringToMultiValueJsonObject(
                                "query", JsonObject.EMPTY.put("request", requestWithQuery)));
            } else {
                deserializedReq = deserializedReq.putAll(deserializeParameters(exchangeWithRequest));
            }

            body = deserializeBody(requestBodyInputStream, exchangeWithRequest)
                    .get("request", JsonObject.EMPTY).get("body");

            errors = null;
        } catch (ApiErrorException e) {
            errors = e.errors;
            body = null;
        }

        deserializedReq = body == null ? deserializedReq : deserializedReq.put("body", body);

        if (errors == null) {
            return JsonObject.EMPTY.put("ok", JsonValue.TRUE).put("request", deserializedReq);
        } else {
            return JsonObject.EMPTY.put("ok", JsonValue.FALSE).put("errors", errors);
        }
    }

    // -------------------------------------------------------------------------
    // Parameter deserialization (uses pre-computed schemas)
    // -------------------------------------------------------------------------

    private JsonObject deserializeParameters(JsonObject exchangeWithRequest) {
        JsonObject request = exchangeWithRequest.get("request", JsonObject.EMPTY).getJsonObject();
        String queryString = URI.create(request.get("uri", JsonValue.EMPTY_STRING).getString()).getQuery();
        JsonObject pathParameters = request.get("pathParameters", JsonObject.EMPTY).getJsonObject();
        JsonObject multiValueQuery = OAS3Deserializer.parseQueryStringToMultiValueJsonObject(
                "queryString",
                JsonObject.EMPTY.put("request", JsonObject.EMPTY.put("queryString", queryString)));
        JsonObject stringModel = JsonObject.EMPTY.put("request", JsonObject.EMPTY
                .put("query", multiValueQuery)
                .put("pathParameters", pathParameters));

        JsonObject validationResult = VALIDATOR.validate(stringModel.jsonValue(), paramSchemaStrings, OutputStructure.DETAILED);
        if (!validationResult.get("valid").getBoolean()) {
            throw new ApiErrorException(
                    API_ERROR_CREATOR.createErrorsObject(validationResult, stringModel.jsonValue(),
                            paramSchemaStrings.as(JsonSchema.class)).jsonValue());
        }

        try {
            JsonObject convertedQuery = OAS3Deserializer.convertStringArrayToSchemaType("query", paramSchema, stringModel);
            return JsonObject.EMPTY.put("query", convertedQuery).put("pathParameters", JsonObject.EMPTY);
        } catch (JsonSchemaValidationException e) {
            throw new ApiErrorException(
                    API_ERROR_CREATOR.createErrorsObject(
                            e.getValidationError(), stringModel.jsonValue(), e.getJsonSchema()).jsonValue());
        }
    }

    // -------------------------------------------------------------------------
    // Body deserialization (uses path-based validation against full document)
    // -------------------------------------------------------------------------

    private JsonObject deserializeBody(InputStream requestBodyInputStream, JsonObject exchangeWithRequest) {
        JsonObject requestBodySpec = operation.get("requestBody", JsonObject.EMPTY).getJsonObject();

        JsonValue contentTypeJsonValue = exchangeWithRequest.get("request", JsonObject.EMPTY)
                .get("headers", JsonObject.EMPTY).get("content-type", JsonArray.EMPTY).get(0);
        MediaType contentTypeHeader = contentTypeJsonValue == null ? null
                : MediaType.parseMediaType(contentTypeJsonValue.getString());

        boolean hasBody = requestBodyInputStream != null
                || exchangeWithRequest.get("request", JsonObject.EMPTY).get("body") != null;

        if (!hasBody) {
            if (requestBodySpec.get("required", JsonValue.FALSE).getBoolean()) {
                throw createMissingBodyError(exchangeWithRequest);
            }
            return exchangeWithRequest;
        } else if (MediaType.APPLICATION_JSON.isCompatibleWith(contentTypeHeader)) {
            return deserializeApplicationJson(requestBodyInputStream, exchangeWithRequest,
                    contentTypeJsonValue.getString());
        } else {
            // Delegate complex form-urlencoded and error handling to OAS3Deserializer
            return OAS3Deserializer.deserializeRequestBody(exchangeWithRequest, requestBodySpec);
        }
    }

    private JsonObject deserializeApplicationJson(InputStream inputStream, JsonObject exchangeWithRequest,
            String contentType) {
        JsonValue bodyRaw = exchangeWithRequest.get("request", JsonObject.EMPTY).get("body");
        JsonValue bodyModel;
        try {
            if (bodyRaw != null) {
                bodyModel = JsonValueFactory.create(bodyRaw.getString());
            } else {
                bodyModel = JsonValueFactory.create(inputStream);
            }
        } catch (Exception e) {
            throw createBadJsonBodyError(exchangeWithRequest);
        }

        // Path-based validation: navigates openApiDocument to the schema so $ref
        // entries resolve against the full document (e.g. #/components/schemas/…)
        JsonArray pathToBodySchema = JsonArray.of("operation", "requestBody", "content", contentType, "schema");
        JsonValue schemaAtPath = openApiDocument.get(pathToBodySchema);
        if (schemaAtPath != null) {
            JsonObject validationResult = VALIDATOR.validate(bodyModel, pathToBodySchema, openApiDocument, OutputStructure.DETAILED);
            if (!validationResult.get("valid").getBoolean()) {
                throw new ApiErrorException(
                        API_ERROR_CREATOR.createErrorsObject(validationResult, bodyModel,
                                schemaAtPath.as(JsonSchema.class)).jsonValue());
            }
        }

        return JsonObject.EMPTY.put("request", JsonObject.EMPTY.put("body", bodyModel));
    }

    // -------------------------------------------------------------------------
    // Parameter $ref resolution (run once in constructor)
    // -------------------------------------------------------------------------

    private static JsonObject resolveRefsInParameters(JsonObject operation, JsonObject openApiDocument) {
        JsonArray parameters = operation.get("parameters", JsonValue.NULL).getJsonArray();
        if (parameters == null) {
            return operation;
        }
        JsonArrayBuilder resolvedParams = JsonArray.EMPTY.builder();
        for (int i = 0; i < parameters.size(); i++) {
            JsonValue param = resolveRef(parameters.get(i), openApiDocument);
            JsonObject paramObj = param.getJsonObject();
            if (paramObj != null) {
                JsonValue schema = paramObj.get("schema");
                if (schema != null) {
                    JsonValue resolvedSchema = resolveRef(schema, openApiDocument);
                    if (resolvedSchema != schema) {
                        paramObj = paramObj.put("schema", resolvedSchema);
                    }
                }
                resolvedParams.add(paramObj);
            } else {
                resolvedParams.add(param);
            }
        }
        return operation.put("parameters", resolvedParams.build().jsonValue());
    }

    private static JsonValue resolveRef(JsonValue value, JsonObject document) {
        if (value == null || !value.isJsonObject()) {
            return value;
        }
        JsonValue ref = value.getJsonObject().get("$ref");
        if (ref == null || !ref.isString()) {
            return value;
        }
        String refStr = ref.getString();
        if (!refStr.startsWith("#/")) {
            return value;
        }
        JsonValue resolved = navigatePointer(refStr.substring(2), document);
        return resolved != null ? resolved : value;
    }

    private static JsonValue navigatePointer(String pointer, JsonObject document) {
        if (pointer.isEmpty()) {
            return document.jsonValue();
        }
        JsonArray segments = StringSplitter.splitString(pointer, '/');
        JsonValue current = document.jsonValue();
        for (int i = 0; i < segments.size(); i++) {
            if (current == null || !current.isJsonObject()) {
                return null;
            }
            current = current.getJsonObject().get(segments.get(i).getString());
        }
        return current;
    }

    // -------------------------------------------------------------------------
    // Parameter schema construction (mirrors OAS3Deserializer.parametersToJsonSchema)
    // -------------------------------------------------------------------------

    private static JsonValue buildParameterSchema(JsonObject operation, boolean itemsAlwaysString) {
        JsonArray oas3parameters = operation.get("parameters", JsonArray.EMPTY).getJsonArray();
        JsonValue additionalQueryParameters = operation.get("additionalQueryParameters", JsonValue.FALSE);
        JsonObject querySchema = JsonObject.EMPTY.put(Keywords.TYPE, "object")
                .put(Keywords.ADDITIONAL_PROPERTIES, additionalQueryParameters);
        JsonObject pathSchema = JsonObject.EMPTY.put(Keywords.TYPE, "object")
                .put(Keywords.ADDITIONAL_PROPERTIES, false);

        for (int i = 0; i < oas3parameters.size(); i++) {
            JsonObject parameter = oas3parameters.get(i).getJsonObject();
            if ("query".equals(parameter.get("in").getString())) {
                querySchema = addParamToSchema(querySchema, parameter, itemsAlwaysString);
            } else if ("path".equals(parameter.get("in").getString())) {
                pathSchema = addParamToSchema(pathSchema, parameter, itemsAlwaysString);
            }
        }
        return JsonObject.EMPTY.put(Keywords.PROPERTIES, JsonObject.EMPTY
                        .put("request", JsonObject.EMPTY
                                .put(Keywords.TYPE, "object")
                                .put(Keywords.ADDITIONAL_PROPERTIES, false)
                                .put(Keywords.PROPERTIES, JsonObject.EMPTY
                                        .put("query", querySchema)
                                        .put("pathParameters", pathSchema))))
                .jsonValue();
    }

    private static JsonObject addParamToSchema(JsonObject schema, JsonObject parameter, boolean itemsAlwaysString) {
        JsonValue name = parameter.get("name");
        String in = parameter.get("in").getString();
        JsonValue maxItems = parameter.get("explode", true).getBoolean() ? null : JsonValue.ONE;
        JsonObjectBuilder propertyValueBuilder = JsonObject.EMPTY.builder();
        if ("path".equals(in)) {
            propertyValueBuilder.put(Keywords.TYPE, itemsAlwaysString
                    ? "string"
                    : parameter.get("schema", JsonObject.EMPTY).getJsonObject()
                      .get(Keywords.ITEMS, parameter.get("schema", JsonObject.EMPTY))
                      .get(Keywords.TYPE, Keywords.STRING).getString());
        } else {
            propertyValueBuilder
                    .put(Keywords.TYPE, "array")
                    .put(Keywords.ITEMS, JsonObject.EMPTY
                            .put(Keywords.TYPE, itemsAlwaysString
                                    ? "string"
                                    : parameter.get("schema", JsonObject.EMPTY).getJsonObject()
                                      .get(Keywords.ITEMS, parameter.get("schema", JsonObject.EMPTY))
                                      .get(Keywords.TYPE, Keywords.STRING).getString()));
            if (maxItems != null) {
                propertyValueBuilder.put(Keywords.MAX_ITEMS, maxItems);
            }
        }
        if (parameter.get("required", false).getBoolean()) {
            schema = schema.put(Keywords.REQUIRED,
                    schema.get(Keywords.REQUIRED, JsonArray.EMPTY).getJsonArray().add(name));
        }
        return schema.put(JsonArray.of(Keywords.PROPERTIES, name), propertyValueBuilder.build());
    }

    // -------------------------------------------------------------------------
    // Error helpers
    // -------------------------------------------------------------------------

    private ApiErrorException createMissingBodyError(JsonObject exchangeWithRequest) {
        JsonValue schema = JsonObject.EMPTY.put(Keywords.PROPERTIES, JsonObject.EMPTY
                .put("request", JsonObject.EMPTY
                        .put(Keywords.REQUIRED, JsonArray.of("body")))).jsonValue();
        return new ApiErrorException(API_ERROR_CREATOR.createErrors(
                VALIDATOR.validate(exchangeWithRequest.jsonValue(), schema, OutputStructure.DETAILED),
                exchangeWithRequest.jsonValue(), schema.as(JsonSchema.class)));
    }

    private static ApiErrorException createBadJsonBodyError(JsonObject exchangeWithRequest) {
        JsonValue instance = exchangeWithRequest.jsonValue();
        JsonSchema schema = JsonObject.EMPTY
                .put("properties", JsonObject.EMPTY
                        .put("request", JsonObject.EMPTY
                                .put("properties", JsonObject.EMPTY
                                        .put("body", false)))).as(JsonSchema.class);
        JsonObject validationResult = VALIDATOR.validate(instance, schema, OutputStructure.DETAILED);
        JsonValue apiError = API_ERROR_CREATOR.createErrors(validationResult, instance, schema);
        apiError = apiError
                .put(JsonArray.of("/request/body", "violations", "/properties/request/properties/body/description"),
                        "Body must be of media type: application/json")
                .put(JsonArray.of("/request/body", "violations", "/properties/request/properties/body/format"), "json")
                .remove(JsonArray.of("/request/body", "rejectedValue"))
                .remove(JsonArray.of("/request/body", "violations", "/properties/request/properties/body"));
        return new ApiErrorException(apiError);
    }
}