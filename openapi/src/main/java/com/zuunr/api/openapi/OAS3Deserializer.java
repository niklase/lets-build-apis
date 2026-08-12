package com.zuunr.api.openapi;

import com.zuunr.json.*;
import com.zuunr.json.schema.JsonSchema;
import com.zuunr.json.schema.Keywords;
import com.zuunr.json.schema.Type;
import com.zuunr.json.schema.validation.JsonSchemaValidator;
import com.zuunr.json.schema.validation.OutputStructure;
import com.zuunr.json.util.ApiErrorCreator;
import com.zuunr.json.util.ApiErrorException;
import com.zuunr.json.util.StringSplitter;
import org.springframework.http.MediaType;

import java.io.InputStream;
import java.net.URI;

public class OAS3Deserializer {

    private static final ApiErrorCreator API_ERROR_CREATOR = new ApiErrorCreator();
    private static final JsonObjectFactory JSON_OBJECT_FACTORY = new JsonObjectFactory();
    private static final JsonSchemaValidator VALIDATOR = new JsonSchemaValidator();
    private static final JsonArray ITEMS_TYPE = JsonArray.of(Keywords.ITEMS, Keywords.TYPE);
    private static final JsonArray ITEMS_PATTERN = JsonArray.of(Keywords.ITEMS, Keywords.PATTERN);
    private static final JsonValue BOOLEAN_PATTERN = JsonValue.of("^true|false$");
    private static final JsonValue NUMBER_PATTERN = JsonValue.of("^-?(?:0|[1-9]\\d*)(?:\\.\\d+)?(?:[eE][+-]?\\d+)?$"); // Should be dynamically fetched from JsonSchemaForJsonSchema?
    private static final JsonValue INTEGER_PATTERN = JsonValue.of("^-?(?:0|[1-9]\\d*)(?:\\d+)?(?:[eE][+-]?\\d+)?$"); // Should be dynamically fetched from JsonSchemaForJsonSchema?

    public static JsonObject deserializeRequest(JsonObject exchangeWithRequestAndBody, JsonObject openApiDef) {
        return deserializeRequest(exchangeWithRequestAndBody, openApiDef, JsonArray.EMPTY);
    }

    public static JsonObject deserializeRequest(JsonObject exchangeWithRequestAndBody, JsonObject openApiDef, JsonArray pathToOperation) {
        return deserializeRequest(exchangeWithRequestAndBody, null, openApiDef, pathToOperation);
    }

    public static JsonObject deserializeRequest(JsonObject exchangeWithRequest, InputStream requestBodyInputStream, JsonObject openApiDef) {
        return deserializeRequest(exchangeWithRequest, requestBodyInputStream, openApiDef, JsonArray.EMPTY);
    }

    public static JsonObject deserializeRequest(JsonObject exchangeWithRequest, InputStream requestBodyInputStream, JsonObject openApiDef, JsonArray pathToOperation) {

        JsonValue errors;
        JsonValue body;
        final JsonObject request = exchangeWithRequest.get("request").getJsonObject();
        JsonObject deserializedReq = JsonObject.EMPTY;
        try {
            JsonObject operation = openApiDef.get(pathToOperation, JsonObject.EMPTY).getJsonObject();
            JsonValue parametersSpec = operation.get("parameters");
            if (parametersSpec == null) {

                JsonValue query = request.get("uri").as(JsonUri.class).getQuery();
                JsonObject requestWithQuery = JsonObject.EMPTY.put("query", query);

                deserializedReq = deserializedReq.put(
                        "query",
                        parseQueryStringToMultiValueJsonObject("query", JsonObject.EMPTY.put("request", requestWithQuery)));
            } else {
                deserializedReq = deserializedReq.putAll(deserializeParameters(exchangeWithRequest, openApiDef, pathToOperation));
            }

            body = deserializeRequestBody(
                    requestBodyInputStream,
                    exchangeWithRequest,
                    openApiDef, pathToOperation)
                    .get("request", JsonObject.EMPTY).get("body");

            errors = null;
        } catch (ApiErrorException apiErrorException) {
            errors = apiErrorException.errors;
            body = null;
        }

        deserializedReq = body == null ? deserializedReq : deserializedReq.put("body", body);

        JsonObject result = errors == null ? JsonObject.EMPTY.put("request", deserializedReq) : JsonObject.EMPTY;
        result = errors == null
                ? result.put("ok", JsonValue.TRUE)
                : result.put("errors", errors).put("ok", JsonValue.FALSE);
        return result;
    }

    public static JsonObject deserializeRequestBody(InputStream requestBodyInputStream, JsonObject exchangeWithoutRequestBody, JsonObject openApiDef, JsonArray pathToOperation) {

        JsonObject errorResult;

        JsonObject openApiRequestBodySpec = openApiDef.get(pathToOperation).get("requestBody", JsonObject.EMPTY).getJsonObject();

        JsonObject openApiRequestBodyContent = openApiRequestBodySpec.get("content", JsonObject.EMPTY).getJsonObject();

        JsonValue contentTypeJsonValue = exchangeWithoutRequestBody.get("request", JsonObject.EMPTY).get("headers", JsonObject.EMPTY).get("content-type", JsonArray.EMPTY).get(0);
        MediaType contentTypeHeader = contentTypeJsonValue == null ? null : MediaType.parseMediaType(contentTypeJsonValue.getString());

        //JsonValue bodyString = exchangeWithoutRequestBody.get("request", JsonObject.EMPTY).get("body");

        if (requestBodyInputStream == null && exchangeWithoutRequestBody.get("request", JsonObject.EMPTY).get("body") == null) {   // TODO: Verify thatinput stream is really null when there is no body
            if (openApiRequestBodySpec.get("required", JsonValue.FALSE).getBoolean()) {
                throw createApiErrorExceptionMissingBody(exchangeWithoutRequestBody);
            } else {
                return exchangeWithoutRequestBody;
            }
        } else if (MediaType.APPLICATION_JSON.isCompatibleWith(contentTypeHeader)) {
            return deserializeApplicationJson(requestBodyInputStream, exchangeWithoutRequestBody, openApiDef, pathToOperation);
        } else if (MediaType.APPLICATION_FORM_URLENCODED.isCompatibleWith(contentTypeHeader)) {
            return deserializeApplicationFormUrlencoded(exchangeWithoutRequestBody, openApiRequestBodyContent);
        } else {
            throw createApiErrorExceptionForUnsupportedContentType(exchangeWithoutRequestBody, openApiRequestBodyContent);
        }
    }

    public static JsonObject deserializeRequestBody(JsonObject exchangeWithRequest, JsonObject openApiDef, JsonArray pathToOperation) throws ApiErrorException {

        JsonObject openApiRequestBodySpec = openApiDef.get(pathToOperation).get("requestBody", JsonObject.EMPTY).getJsonObject();
        JsonObject errorResult;

        JsonObject openApiRequestBodyContent = openApiRequestBodySpec.get("content", JsonObject.EMPTY).getJsonObject();

        JsonValue contentTypeJsonValue = exchangeWithRequest.get("request", JsonObject.EMPTY).get("headers", JsonObject.EMPTY).get("content-type", JsonArray.EMPTY).get(0);
        MediaType contentTypeHeader = contentTypeJsonValue == null ? null : MediaType.parseMediaType(contentTypeJsonValue.getString());

        JsonValue bodyString = exchangeWithRequest.get("request", JsonObject.EMPTY).get("body");

        JsonValue bodyModel = bodyString;
        JsonValue finalRequestSchema;

        if (bodyString == null) {
            if (openApiRequestBodySpec.get("required", JsonValue.FALSE).getBoolean()) {
                throw createApiErrorExceptionMissingBody(exchangeWithRequest);
            } else {
                return exchangeWithRequest;
            }
        } else if (MediaType.APPLICATION_JSON.isCompatibleWith(contentTypeHeader)) {
            return deserializeApplicationJson(exchangeWithRequest, openApiDef, pathToOperation);
        } else if (MediaType.APPLICATION_FORM_URLENCODED.isCompatibleWith(contentTypeHeader)) {
            return deserializeApplicationFormUrlencoded(exchangeWithRequest, openApiRequestBodyContent);
        } else {
            throw createApiErrorExceptionForUnsupportedContentType(exchangeWithRequest, openApiRequestBodyContent);
        }
    }

    private static JsonObject deserializeApplicationFormUrlencoded(JsonObject exchangeWithRequest, JsonObject openApiRequestBodyContent) {

        JsonValue bodyModel;
        JsonValue finalRequestSchema;

        JsonValue schemaOfBody = openApiRequestBodyContent
                .get(MediaType.APPLICATION_FORM_URLENCODED_VALUE, JsonObject.EMPTY)
                .get("schema");

        if (schemaOfBody == null) {
            throw createApiErrorExceptionForUnsupportedContentType(exchangeWithRequest, openApiRequestBodyContent);
        }

        JsonValue stringTypesSchema = schemaOfBody.isBoolean()
                ? schemaOfBody
                : makeAllItemTypesString(schemaOfBody).jsonValue();

        bodyModel = createBodyModelForFormUrlEncoded(exchangeWithRequest, stringTypesSchema);

        JsonObject oasProperties = schemaOfBody
                .get(Keywords.PROPERTIES, JsonObject.EMPTY).getJsonObject();
        JsonObject stringTypesSchemaProperties = stringTypesSchema.get(Keywords.PROPERTIES, JsonObject.EMPTY).getJsonObject();
        JsonArray stringTypesSchemaPropertiesKeys = stringTypesSchemaProperties.keys();
        JsonArray stringTypesSchemaPropertiesValues = stringTypesSchemaProperties.values();
        JsonObjectBuilder propertiesBuilder = stringTypesSchema
                .get(Keywords.PROPERTIES, JsonObject.EMPTY).getJsonObject().builder();

        for (int i = 0; i < stringTypesSchemaPropertiesValues.size(); i++) {
            JsonObject stringTypesPropertySchema = stringTypesSchemaPropertiesValues.get(i).getJsonObject();

            JsonValue oasPropertySchema = oasProperties.get(
                    stringTypesSchemaPropertiesKeys.get(i).getString());

            JsonValue oasRootType = oasPropertySchema.get(Keywords.TYPE, JsonValue.NULL);
            JsonValue oasType;
            // If OAS schema is array
            if ("array".equals(oasRootType.getString())) {
                oasType = oasPropertySchema.get(Keywords.ITEMS, JsonObject.EMPTY).get(Keywords.TYPE, "string");
                oasType = oasPropertySchema.get(Keywords.ITEMS, JsonObject.EMPTY).get(Keywords.TYPE, "string");
            } else {
                oasType = oasRootType;
            }

            JsonObject stringTypesItemsSchema = stringTypesPropertySchema.get(Keywords.ITEMS).getJsonObject();
            JsonObject updatedProperty = stringTypesPropertySchema.put(Keywords.ITEMS, stringTypesItemsSchema.put(Keywords.TYPE, oasType));
            propertiesBuilder.put(stringTypesSchemaPropertiesKeys.get(i).getString(), updatedProperty);
        }

        JsonObject correctTypeStillMultiValueSchema = propertiesBuilder.build();

        try {
            finalRequestSchema = JsonObject.EMPTY.put(Keywords.PROPERTIES, JsonObject.EMPTY.put("request", JsonObject.EMPTY.put(Keywords.PROPERTIES, JsonObject.EMPTY.put("body", JsonObject.EMPTY.put(Keywords.PROPERTIES, correctTypeStillMultiValueSchema))))).jsonValue();

            bodyModel = convertStringArrayToSchemaType(
                    "body",
                    finalRequestSchema,
                    JsonObject.EMPTY.put("request", JsonObject.EMPTY.put("body", bodyModel.getJsonObject()))).jsonValue();
        } catch (JsonSchemaValidationException e) {
            JsonValue apiError = API_ERROR_CREATOR.createErrors(e.getValidationError(), bodyModel, e.getJsonSchema());
            throw new ApiErrorException(apiError);
        }

        if (bodyModel != null) {
            JsonValue requestWithBody = JsonObject.EMPTY.put("body", bodyModel).jsonValue();
            JsonObject validationResult = VALIDATOR.validate(
                    requestWithBody,
                    finalRequestSchema,
                    OutputStructure.DETAILED
            );

            if (!validationResult.get("valid").getBoolean()) {
                throw new ApiErrorException(API_ERROR_CREATOR.createErrorsObject(
                                validationResult,
                                requestWithBody,
                                finalRequestSchema.as(JsonSchema.class))
                        .jsonValue());
            }
        }

        JsonObject requestResult = JsonObject.EMPTY;
        requestResult = bodyModel == null ? requestResult : requestResult.put("body", bodyModel);
        return JsonObject.EMPTY.put("request", requestResult);
    }

    private static JsonValue createBodyModelForFormUrlEncoded(JsonObject exchangeWithRequestBodyString, JsonValue stringTypesSchema) {

        JsonValue bodyModel;
        JsonValue bodyString = exchangeWithRequestBodyString.get("request").get("body");

        try {
            // Create multi value request body where all fields are arrays of strings
            bodyModel = parseBodyStringToMultiValueJsonObject(
                    "body",
                    exchangeWithRequestBodyString
            ).jsonValue();
        } catch (JsonSchemaValidationException jsonSchemaValidationException) {

            JsonValue instance = exchangeWithRequestBodyString.jsonValue();
            JsonSchema schema = jsonSchemaValidationException.getJsonSchema();
            JsonObject validationResult = VALIDATOR.validate(
                    instance,
                    schema,
                    OutputStructure.DETAILED);
            JsonValue apiError = API_ERROR_CREATOR.createErrors(validationResult, instance, schema);
            throw new ApiErrorException(apiError);
        }

        JsonObject validationResult = VALIDATOR.validate(
                bodyModel,
                stringTypesSchema,
                OutputStructure.DETAILED
        );

        if (!validationResult.get("valid").getBoolean()) {
            JsonValue apiError = API_ERROR_CREATOR.createErrors(validationResult, bodyModel, stringTypesSchema.as(JsonSchema.class));
            throw new ApiErrorException(apiError);
        }
        return bodyModel;
    }

    private static JsonObject deserializeApplicationJson(InputStream requestBodyInputStream, JsonObject exchangeWithRequest, JsonObject openApiDef, JsonArray pathToOperation) {
        JsonObject openApiRequestBodyContent = openApiDef.get(pathToOperation).getJsonObject().get("requestBody", JsonObject.EMPTY).getJsonObject().get("content", JsonObject.EMPTY).getJsonObject();
        JsonValue schemaOfBody = openApiRequestBodyContent
                .get(MediaType.APPLICATION_JSON_VALUE, JsonObject.EMPTY)
                .get("schema");

        if (schemaOfBody == null) {
            throw createApiErrorExceptionForUnsupportedContentType(exchangeWithRequest, openApiRequestBodyContent);
        } else {
            JsonValue bodyModel;
            try {

                bodyModel = exchangeWithRequest.get("request", JsonObject.EMPTY).get("body");

                if (bodyModel != null) {
                    bodyModel = JsonValueFactory.create(bodyModel.getString());
                }
                if (bodyModel == null) {
                    bodyModel = JsonValueFactory.create(requestBodyInputStream);
                }
            } catch (Exception e) {
                throw createApiErrorExceptionForBadJsonBody(JsonObject.EMPTY.put("request", JsonObject.EMPTY.put("body", "MUST BE JSON!")));
            }

            JsonValue finalRequestSchema = JsonObject.EMPTY
                    .put("components", JsonObject.EMPTY
                            .put("schemas", openApiDef.get("components", JsonObject.EMPTY).get("schemas", JsonObject.EMPTY)))
                    .put(Keywords.PROPERTIES, JsonObject.EMPTY
                            .put("request", JsonObject.EMPTY
                                    .put("properties", JsonObject.EMPTY
                                            .put("body", schemaOfBody)))).jsonValue();
            JsonObject finalRequestModel = JsonObject.EMPTY.put("request", JsonObject.EMPTY.put("body", bodyModel));
            validateRequestModel(finalRequestModel, finalRequestSchema);
            return finalRequestModel;
        }
    }

    private static JsonObject deserializeApplicationJson(JsonObject exchangeWithRequest, JsonObject openApiDef, JsonArray pathToOperation) {
        return deserializeApplicationJson(null, exchangeWithRequest, openApiDef, pathToOperation);
    }


    private static ApiErrorException createApiErrorExceptionMissingBody(JsonObject exchangeWithRequest) {
        JsonValue schema = JsonObject.EMPTY.put(Keywords.PROPERTIES, JsonObject.EMPTY.put("request", JsonObject.EMPTY.put(Keywords.REQUIRED, JsonArray.of("body")))).jsonValue();
        return new ApiErrorException(API_ERROR_CREATOR.createErrors(VALIDATOR.validate(exchangeWithRequest.jsonValue(), schema, OutputStructure.DETAILED), exchangeWithRequest.jsonValue(), schema.as(JsonSchema.class)));
    }

    private static ApiErrorException createApiErrorExceptionForBadJsonBody(JsonObject exchangeWithRequest) {
        JsonValue instance = exchangeWithRequest.jsonValue();
        JsonSchema schema = JsonObject.EMPTY
                .put("properties", JsonObject.EMPTY
                        .put("request", JsonObject.EMPTY
                                .put("properties", JsonObject.EMPTY
                                        .put("body", false
                                        )))).as(JsonSchema.class);
        JsonObject validationResult = VALIDATOR.validate(
                instance,
                schema,
                OutputStructure.DETAILED);

        JsonValue apiError = API_ERROR_CREATOR.createErrors(validationResult, instance, schema);
        apiError = apiError
                .put(JsonArray.of("/request/body", "violations", "/properties/request/properties/body/description"), "Body must be of media type: application/json")
                .put(JsonArray.of("/request/body", "violations", "/properties/request/properties/body/format"), "json")
                .remove(JsonArray.of("/request/body", "rejectedValue"))
                .remove(JsonArray.of("/request/body", "violations", "/properties/request/properties/body"));

        return new ApiErrorException(apiError);
    }

    private static void validateRequestModel(JsonObject requestModel, JsonValue finalRequestSchema) throws ApiErrorException {
        JsonObject validationResult = VALIDATOR.validate(
                requestModel.jsonValue(),
                finalRequestSchema,
                OutputStructure.DETAILED
        );

        if (!validationResult.get("valid").getBoolean()) {
            throw new ApiErrorException(API_ERROR_CREATOR.createErrorsObject(
                            validationResult,
                            requestModel.jsonValue(),
                            finalRequestSchema.as(JsonSchema.class))
                    .jsonValue());
        }
    }


    private static JsonObject makeAllItemTypesString(JsonValue schema) {
        String example = """
                 {
                     "properties": {
                         "name": {
                             "item": {
                                 "type":"string"
                             },
                            "type": "array"
                        }
                    }
                }
                """;

        JsonObject properties = schema.get(Keywords.PROPERTIES, JsonObject.EMPTY).getJsonObject();
        JsonArray propertiesKeys = properties.keys();
        JsonArray propertiesValues = properties.values();

        JsonObjectBuilder propertiesBuilder = properties.builder();
        for (int i = 0; i < propertiesValues.size(); i++) {

            boolean specifiedAsArrayInOasSchema = false;

            JsonValue propertyValue = propertiesValues.get(i);
            if (propertyValue.isJsonObject()) {
                specifiedAsArrayInOasSchema = Type.ARRAY.toString().toLowerCase().equals(propertyValue.getJsonObject().get(Keywords.TYPE, JsonValue.NULL).getString());
            }

            JsonValue itemsSchema = propertyValue.get(Keywords.ITEMS);
            if (itemsSchema == null || itemsSchema.isBoolean()) {
                itemsSchema = JsonObject.EMPTY.jsonValue();
            }

            JsonObject updatedPropertyValue;

            if (specifiedAsArrayInOasSchema) {
                updatedPropertyValue = propertyValue.getJsonObject();
            } else {
                updatedPropertyValue = JsonObject.EMPTY;
                updatedPropertyValue = updatedPropertyValue.put(Keywords.MAX_ITEMS, 1);
                updatedPropertyValue = updatedPropertyValue.put(Keywords.MIN_ITEMS, 1);
            }

            updatedPropertyValue = updatedPropertyValue
                    .put(Keywords.TYPE, "array")
                    .put(Keywords.ITEMS, itemsSchema.getJsonObject()
                            .put(Keywords.TYPE, "string"));

            propertiesBuilder
                    .put(propertiesKeys.get(i).getString(), updatedPropertyValue);
        }

        return schema.getJsonObject().put(Keywords.PROPERTIES, propertiesBuilder.build());
    }

    public static JsonObject deserializeParameters(JsonObject exchangeWithRequest, JsonObject openApiDef, JsonArray pathToOperation) throws ApiErrorException {

        JsonObject operation = openApiDef.get(pathToOperation).getJsonObject();
        JsonArray openApiParameters = operation.get("parameters", JsonArray.EMPTY).getJsonArray();
        JsonObject errorResult;
        JsonValue parameterSchemaWhereItemsAreStrings = parametersToJsonSchema(operation, true);
        JsonValue parameterSchema = parametersToJsonSchema(operation, false);
        JsonObject request = exchangeWithRequest.get("request", JsonObject.EMPTY).getJsonObject();
        String queryString = URI.create(request.get("uri", JsonValue.EMPTY_STRING).getString()).getQuery();
        JsonObject pathParameters = request.get("pathParameters", JsonObject.EMPTY).getJsonObject();
        JsonObject multiValueParametersForQuery = parseQueryStringToMultiValueJsonObject("queryString", JsonObject.EMPTY.put("request", JsonObject.EMPTY.put("queryString", queryString)));
        JsonObject stringModel = JsonObject.EMPTY.put("request", JsonObject.EMPTY
                .put("query", multiValueParametersForQuery)
                .put("pathParameters", pathParameters)
        );

        JsonObject validationResult = VALIDATOR.validate(
                stringModel.jsonValue(),
                parameterSchemaWhereItemsAreStrings,
                OutputStructure.DETAILED
        );

        if (!validationResult.get("valid").getBoolean()) {
            throw new ApiErrorException(API_ERROR_CREATOR.createErrorsObject(validationResult, stringModel.jsonValue(), parameterSchemaWhereItemsAreStrings.as(JsonSchema.class)).jsonValue());
        } else {
            try {
                JsonObject convertedModelQuery = convertStringArrayToSchemaType("query", parameterSchema, stringModel);
                return JsonObject.EMPTY
                        .put("query", convertedModelQuery)
                        .put("pathParameters", JsonObject.EMPTY);
            } catch (JsonSchemaValidationException e) {
                throw new ApiErrorException(
                        API_ERROR_CREATOR.createErrorsObject(
                                e.getValidationError(),
                                stringModel.jsonValue(),
                                e.getJsonSchema()).jsonValue());
            }
        }
    }

    static JsonObject convertStringArrayToSchemaType(String fieldNameInRequestModel, JsonValue exchangeSchemaForRequest, JsonObject
            exchangeModelWithRequestMultiValueString) {

        String propertyName = fieldNameInRequestModel;
        JsonObject propertiesOfQuerySchema = exchangeSchemaForRequest
                .get(Keywords.PROPERTIES, JsonObject.EMPTY)
                .get("request", JsonObject.EMPTY)
                .get(Keywords.PROPERTIES, JsonObject.EMPTY)
                .get(propertyName, JsonObject.EMPTY)
                .get(Keywords.PROPERTIES, JsonObject.EMPTY).getJsonObject();

        JsonObject queryAsMultiValue = exchangeModelWithRequestMultiValueString.get("request", JsonObject.EMPTY).get(fieldNameInRequestModel).getJsonObject();
        JsonArray propertyKeys = propertiesOfQuerySchema.keys();
        JsonArray propertyValues = propertiesOfQuerySchema.values();

        JsonObjectBuilder convertedMultiValueParametersBuilder = queryAsMultiValue.builder();
        for (int i = 0; i < propertiesOfQuerySchema.size(); i++) {
            String firstLevelType = propertyValues.get(i).get(Keywords.TYPE, Keywords.STRING).getString();

            String key = propertyKeys.get(i).getString();
            JsonArray stringArray = queryAsMultiValue.get(key, JsonValue.NULL).getJsonArray();

            if (Keywords.ARRAY.equals(firstLevelType)
                && JsonValue.ONE.equals(propertyValues.get(i).get(Keywords.MAX_ITEMS)) // explode = false
            ) {
                JsonArray values = queryAsMultiValue.get(propertyKeys.get(i).getString(), JsonArray.EMPTY).getJsonArray();
                if (!values.isEmpty()) {
                    stringArray = JsonArray.of((Object[]) values.get(0).getString().split(","));
                }
            }

            String itemsType = propertyValues.get(i).get(JsonArray.of(Keywords.ITEMS, Keywords.TYPE), JsonValue.of(Keywords.STRING)).getString();

            JsonArrayBuilder convertedValuesBuilder = JsonArray.EMPTY.builder();
            if (stringArray != null) {
                for (int j = 0; j < stringArray.size(); j++) {

                    JsonValue convertedValue;
                    try {
                        convertedValue = Keywords.STRING.equals(itemsType)
                                ? stringArray.get(j)
                                : JSON_OBJECT_FACTORY.createJsonValue(stringArray.get(j).getString());
                    } catch (Exception jsonParseException) {
                        throw createJsonSchemaValidationException(exchangeSchemaForRequest, propertiesOfQuerySchema, exchangeModelWithRequestMultiValueString);
                    }
                    switch (itemsType) {
                        case Keywords.INTEGER: {
                            if (!convertedValue.isJsonNumber() || !convertedValue.getJsonNumber().isJsonInteger()) {
                                throw createJsonSchemaValidationException(exchangeSchemaForRequest, propertiesOfQuerySchema, exchangeModelWithRequestMultiValueString);
                            }
                            break;
                        }
                        case Keywords.NUMBER: {
                            if (!convertedValue.isJsonNumber()) {
                                throw createJsonSchemaValidationException(exchangeSchemaForRequest, propertiesOfQuerySchema, exchangeModelWithRequestMultiValueString);
                            }
                            break;
                        }
                        case Keywords.BOOLEAN: {
                            if (!convertedValue.isBoolean()) {
                                throw createJsonSchemaValidationException(exchangeSchemaForRequest, propertiesOfQuerySchema, exchangeModelWithRequestMultiValueString);
                            }
                            break;
                        }
                    }
                    convertedValuesBuilder.add(convertedValue);
                }

                convertedMultiValueParametersBuilder.put(key, convertedValuesBuilder.build());
            }
        }
        return convertedMultiValueParametersBuilder.build();
    }

    private static JsonSchemaValidationException createJsonSchemaValidationException(
            JsonValue schema, JsonObject
                    propertiesOfQuerySchema,
            JsonObject queryAsMultiValue) {
        JsonSchema updatedSchema = schema.put(JsonArray.of(Keywords.PROPERTIES, "query", Keywords.PROPERTIES), schemaOfConvertibleStrings(propertiesOfQuerySchema)).as(JsonSchema.class);
        throw new JsonSchemaValidationException(
                VALIDATOR.validate(queryAsMultiValue.jsonValue(), updatedSchema, OutputStructure.DETAILED), updatedSchema
        );
    }

    private static JsonValue parametersToJsonSchema(JsonObject openApiSpec, boolean itemsTypeAlwaysString) {

        JsonArray oas3parameters = openApiSpec.get("parameters", JsonArray.EMPTY).getJsonArray();
        JsonValue additionalQueryParameters = openApiSpec.get("additionalQueryParameters", JsonValue.FALSE);
        JsonObject querySchema = JsonObject.EMPTY.put(Keywords.TYPE, "object").put(Keywords.ADDITIONAL_PROPERTIES, additionalQueryParameters);
        JsonObject pathSchema = JsonObject.EMPTY.put(Keywords.TYPE, "object").put(Keywords.ADDITIONAL_PROPERTIES, false);

        for (int i = 0; i < oas3parameters.size(); i++) {
            JsonObject parameter = oas3parameters.get(i).getJsonObject();

            if ("query".equals(parameter.get("in").getString())) {
                querySchema = addParameterToSchema(querySchema, parameter, itemsTypeAlwaysString);
            } else if ("path".equals(parameter.get("in").getString())) {
                pathSchema = addParameterToSchema(pathSchema, parameter, itemsTypeAlwaysString);
            }
        }
        return JsonObject.EMPTY.put(Keywords.PROPERTIES, JsonObject.EMPTY
                        .put("request", JsonObject.EMPTY
                                .put(Keywords.TYPE, "object")
                                .put(Keywords.ADDITIONAL_PROPERTIES, false)
                                .put(Keywords.PROPERTIES, JsonObject.EMPTY
                                        .put("query", querySchema)
                                        .put("pathParameters", pathSchema)
                                )))
                .jsonValue();
    }

    private static JsonObject addParameterToSchema(JsonObject parameterSchemaToBeUpdated, JsonObject parameter, boolean itemsTypeAlwaysString) {
        JsonValue name = parameter.get("name");
        String in = parameter.get("in").getString();
        JsonValue maxItems = parameter.get("explode", true).getBoolean() ? null : JsonValue.ONE;
        JsonObjectBuilder propertyValueBuilder = JsonObject.EMPTY.builder();
        if ("path".equals(in)) {
            propertyValueBuilder.put(Keywords.TYPE, itemsTypeAlwaysString
                    ? "string"
                    : parameter.get("schema", JsonObject.EMPTY).getJsonObject()
                      .get(Keywords.ITEMS, parameter.get("schema", JsonObject.EMPTY))
                      .get(Keywords.TYPE, Keywords.STRING).getString());
        } else { // both query and header parameters are possibly multi-valued
            propertyValueBuilder
                    .put(Keywords.TYPE, "array")
                    .put(Keywords.ITEMS, parameter.get("schema", JsonObject.EMPTY).getJsonObject()
                            .get(Keywords.ITEMS, JsonObject.EMPTY).getJsonObject()
                            .put(Keywords.TYPE, itemsTypeAlwaysString
                                    ? "string"
                                    : parameter.get("schema", JsonObject.EMPTY).getJsonObject()
                                      .get(Keywords.ITEMS, parameter.get("schema", JsonObject.EMPTY))
                                      .get(Keywords.TYPE, Keywords.STRING).getString()));
            if (maxItems != null) {
                propertyValueBuilder.put(Keywords.MAX_ITEMS, maxItems);
            }
        }

        if (parameter.get("required", false).getBoolean()) {
            parameterSchemaToBeUpdated = parameterSchemaToBeUpdated.put(Keywords.REQUIRED, parameterSchemaToBeUpdated.get(Keywords.REQUIRED, JsonArray.EMPTY).getJsonArray().add(name));
        }
        parameterSchemaToBeUpdated = parameterSchemaToBeUpdated
                .put(JsonArray.of(Keywords.PROPERTIES, name), propertyValueBuilder.build());
        return parameterSchemaToBeUpdated;
    }

    public static JsonObject parseQueryStringToMultiValueJsonObject(String fieldNameInRequestModel, JsonObject
            exchangeWithRequest) {
        return parseStringModelToJsonArray(fieldNameInRequestModel, exchangeWithRequest, "Query parameters names must be separated from parameter value(s) by '='");
    }

    public static JsonObject parseBodyStringToMultiValueJsonObject(String fieldNameInRequestModel, JsonObject
            exchangeWithRequest) {
        return parseStringModelToJsonArray(fieldNameInRequestModel, exchangeWithRequest, "Request body must be of media type: " + MediaType.APPLICATION_FORM_URLENCODED_VALUE);
    }

    public static JsonObject parseStringModelToJsonArray(String fieldNameInRequestModel, JsonObject
            exchangeWithRequest, String stringSchemaDescription) throws JsonSchemaValidationException {
        JsonObject queryStringParameters = JsonObject.EMPTY;

        String stringToParse = exchangeWithRequest.get("request").get(fieldNameInRequestModel, JsonValue.NULL).getString();

        if (stringToParse == null || "".equals(stringToParse)) {
            return queryStringParameters;
        }
        JsonArray parsedQuery = StringSplitter.splitString(stringToParse, '&');
        for (JsonValue pair : parsedQuery) {

            int index = pair.getString().indexOf('=');
            if (index == -1) {
                JsonSchema jsonSchema = JsonObject.EMPTY
                        .put("properties", JsonObject.EMPTY
                                .put("request", JsonObject.EMPTY
                                        .put("properties", JsonObject.EMPTY
                                                .put(fieldNameInRequestModel, JsonObject.EMPTY
                                                        .put("pattern", "^([^&=]+=[^&]*)?(&[^&=]*=([^&]*))*$")
                                                        .put("description", stringSchemaDescription)
                                                ))))
                        .as(JsonSchema.class);
                JsonObject validationResult = VALIDATOR.validate(exchangeWithRequest.jsonValue(), jsonSchema.asJsonValue(), OutputStructure.DETAILED);
                throw new ApiErrorException(API_ERROR_CREATOR.createErrors(validationResult, exchangeWithRequest.jsonValue(), jsonSchema));
            }
            String name = pair.getString().substring(0, index);
            String value = pair.getString().substring(index + 1);

            JsonArray queryParameterValues = queryStringParameters.get(name, JsonArray.EMPTY).getJsonArray();
            queryStringParameters = queryStringParameters.put(name, queryParameterValues.add(value));
        }
        return queryStringParameters;
    }

    private static JsonObject schemaOfConvertibleStrings(JsonObject properties) {

        JsonObjectBuilder propertiesBuilder = JsonObject.EMPTY.builder();

        JsonArray keys = properties.keys();
        JsonArray values = properties.values();
        for (int i = 0; i < properties.size(); i++) {
            JsonValue value = values.get(i);
            String type = value.get(ITEMS_TYPE, JsonValue.of(Keywords.STRING)).getString();
            String propertyKey = keys.get(i).getString();
            switch (type) {
                case Keywords.STRING: {
                    propertiesBuilder.put(propertyKey, value);
                    break;
                }
                case Keywords.BOOLEAN: {
                    propertiesBuilder.put(propertyKey, value.put(ITEMS_TYPE, Keywords.STRING).put(ITEMS_PATTERN, BOOLEAN_PATTERN));
                    break;
                }
                case Keywords.INTEGER: {
                    propertiesBuilder.put(propertyKey, value.put(ITEMS_TYPE, Keywords.STRING).put(ITEMS_PATTERN, INTEGER_PATTERN));
                    break;
                }
                case Keywords.NUMBER: {
                    propertiesBuilder.put(propertyKey, value.put(ITEMS_TYPE, Keywords.STRING).put(ITEMS_PATTERN, NUMBER_PATTERN));
                    break;
                }
            }
        }
        return propertiesBuilder.build();
    }

    private static JsonValue getRequestSchemaForContentType(JsonObject oasRequestBodyContent) {

        JsonArrayBuilder jsonArrayBuilder = JsonArray.EMPTY.builder();
        for (int i = 0; i < oasRequestBodyContent.keys().size(); i++) {
            jsonArrayBuilder.add(JsonObject.EMPTY.put("pattern", "^" + oasRequestBodyContent.keys().get(i).getString().replace("/", "[/]") + "([+;].*)?$"));
        }

        return JsonObject.EMPTY
                .put(Keywords.PROPERTIES, JsonObject.EMPTY
                        .put("request", JsonObject.EMPTY
                                .put(Keywords.REQUIRED, JsonArray.of("headers"))
                                .put(Keywords.PROPERTIES, JsonObject.EMPTY
                                        .put("headers", JsonObject.EMPTY
                                                .put(Keywords.REQUIRED, JsonArray.of("content-type"))
                                                .put(Keywords.PROPERTIES, JsonObject.EMPTY
                                                        .put("content-type", JsonObject.EMPTY
                                                                .put("items", JsonObject.EMPTY
                                                                        .put(Keywords.ANY_OF, jsonArrayBuilder.build())))))))).jsonValue();
    }

    private static ApiErrorException createApiErrorExceptionForUnsupportedContentType(JsonObject exchangeWithRequest, JsonObject openApiRequestBodyContent) {
        JsonValue finalRequestSchema = getRequestSchemaForContentType(openApiRequestBodyContent);

        JsonObject validationResult = VALIDATOR.validate(exchangeWithRequest.jsonValue(), finalRequestSchema, OutputStructure.DETAILED);
        if (validationResult.get("valid", JsonValue.FALSE).getBoolean()) {
            return null;
        }
        return new ApiErrorException(API_ERROR_CREATOR.createErrors(validationResult, exchangeWithRequest.jsonValue(), finalRequestSchema.as(JsonSchema.class)));
    }

}
