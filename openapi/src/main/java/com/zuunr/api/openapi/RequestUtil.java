package com.zuunr.api.openapi;

import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonObjectBuilder;
import com.zuunr.json.JsonValue;
import com.zuunr.json.schema.JsonSchema;
import com.zuunr.json.schema.Type;
import com.zuunr.json.schema.validation.JsonSchemaValidator;
import com.zuunr.json.schema.validation.OutputStructure;
import com.zuunr.json.schema.validation.node.string.Pattern;
import com.zuunr.json.util.StringSplitter;

import java.math.BigDecimal;
import java.util.Iterator;

/**
 * @author Niklas Eldberger
 */
public class RequestUtil {

    private static final JsonSchemaValidator VALIDATOR = new JsonSchemaValidator();

    public static JsonObject convertToSchemaType(JsonObject formStyle, char separator, JsonSchema schema) {

        JsonObjectBuilder explosionBuilder = formStyle.builder();

        JsonArray keys = formStyle.keys();
        JsonArray values = formStyle.values();

        for (int i = 0; i < keys.size(); i++) {

            String key = keys.get(i).getString();

            JsonObject properties = schema.getProperties();

            JsonValue subSchema = properties == null ? null : properties.get(key);

            if (subSchema == null) {

                JsonObject patternProperties = schema.getPatternProperties();
                if (patternProperties != null) {
                    Iterator<JsonValue> patternPropertiesIter = patternProperties.values().iterator();
                    for (JsonValue patternJsonValue : schema.getPatternProperties().keys()) {
                        JsonValue next = patternPropertiesIter.next();
                        if (patternJsonValue.as(Pattern.class).compiled().matcher(key).find()) {
                            subSchema = next;
                            break;
                        }
                    }
                }
            }

            if (subSchema == null) {
                subSchema = schema.getAdditionalProperties() == null ? null : schema.getAdditionalProperties().asJsonValue();
            }

            if (subSchema != null) {
                JsonValue typeJsonValue = subSchema.get("type");
                if (typeJsonValue.isString()) {
                    convertToType(key, values.get(i).get(0), Type.valueOf(typeJsonValue.getString().toUpperCase()), separator, explosionBuilder);
                } else if (typeJsonValue.isJsonArray()) {

                    boolean converted = false;
                    for (int typeIndex = 0; typeIndex < typeJsonValue.getJsonArray().size(); typeIndex++) {
                        try {
                            convertToType(key, values.get(i).get(0), Type.valueOf(typeJsonValue.getJsonArray().get(typeIndex).getString().toUpperCase()), separator, explosionBuilder);
                            converted = true;
                        } catch (NumberFormatException e) {
                            // continue
                        }
                    }
                    if (!converted) {
                        convertToType(key, values.get(i).get(0), Type.STRING, separator, explosionBuilder);
                    }
                } else {
                    throw new RuntimeException("type must be either string or array");
                }
            }
        }
        return explosionBuilder.build();
    }

    private static void convertToType(String key, JsonValue value, Type type, char separator, JsonObjectBuilder explosionBuilder) {
        switch (type) {
            case STRING: {
                explosionBuilder.put(key, value.getString());
                break;
            }
            case ARRAY: {
                explosionBuilder.put(key, StringSplitter.splitString(value.getString(), separator));
                break;
            }
            case NUMBER: {
                explosionBuilder.put(key, new BigDecimal(value.getString()));
                break;
            }
            case INTEGER: {
                explosionBuilder.put(key, Long.valueOf(value.getString()));
                break;
            }
            case BOOLEAN: {
                explosionBuilder.put(key, Boolean.valueOf(value.getString()));
                break;
            }
            case OBJECT: {
                throw new RuntimeException("Not implemented yet!");
            }
            case NULL: {
                throw new RuntimeException("Not implemented yet!");
            }
        }
    }

    public static JsonObject explode(JsonObject formStyle, char separator) {

        JsonObjectBuilder explosionBuilder = JsonObject.EMPTY.builder();

        JsonArray keys = formStyle.keys();
        JsonArray values = formStyle.values();

        for (int i = 0; i < keys.size(); i++) {
            explosionBuilder.put(keys.get(i).getString(), StringSplitter.splitString(values.get(i).getJsonArray().get(0).getString(), separator));
        }
        return explosionBuilder.build();
    }

    public static JsonObject queryParametersFormStyle(String query) throws JsonSchemaValidationException {
        JsonObject queryStringParameters = JsonObject.EMPTY;

        JsonArray parsedQuery = StringSplitter.splitString(query, '&');
        for (JsonValue pair : parsedQuery) {

            int index = pair.getString().indexOf('=');
            if (index == -1) {
                JsonSchema jsonSchema = JsonObject.EMPTY.put("pattern", "^[^&]([^&=]*(=[^&]*))?(&[^&=]*=([^&]*))*$").as(JsonSchema.class);
                JsonObject validationResult = VALIDATOR.validate(JsonValue.of(query), jsonSchema, OutputStructure.DETAILED);
                throw new JsonSchemaValidationException(validationResult, jsonSchema);
            }
            String name = pair.getString().substring(0, index);
            String value = pair.getString().substring(index + 1);

            JsonArray queryParameterValues = queryStringParameters.get(name, JsonArray.EMPTY).getJsonArray();
            queryStringParameters = queryStringParameters.put(name, queryParameterValues.add(value));
        }
        return queryStringParameters;
    }

    // Info about the parameterConfig alternatives: https://swagger.io/specification/
    public static JsonObject parseQuery(String query, JsonObject parameterConfig) throws JsonSchemaValidationException {
        JsonObject queryStringParameters = JsonObject.EMPTY;

        JsonArray parsedQuery = StringSplitter.splitString(query, '&');
        for (JsonValue pair : parsedQuery) {

            int index = pair.getString().indexOf('=');
            if (index == -1) {
                JsonSchema jsonSchema = JsonObject.EMPTY.put("pattern", "^[^&]([^&=]*(=[^&]*))?(&[^&=]*=([^&]*))*$").as(JsonSchema.class);
                JsonObject validationResult = VALIDATOR.validate(JsonValue.of(query), jsonSchema, OutputStructure.DETAILED);
                throw new JsonSchemaValidationException(validationResult, jsonSchema);
            }
            String queryParamName = pair.getString().substring(0, index);
            String value = pair.getString().substring(index + 1);

            JsonObject config = parameterConfig.get(queryParamName).getJsonObject();
            String style = config.get("style").getString();
            String type = config.get("type").getString();
            boolean explode = config.get("explode", true).getBoolean();

            if ("array".equals(type)) {
                if ("form".equals(style)) {
                    JsonArray queryParameterValues = queryStringParameters.get(queryParamName, JsonArray.EMPTY).getJsonArray();

                    if (queryParameterValues.isEmpty()) {
                        queryStringParameters = queryStringParameters.put(queryParamName, queryParameterValues.add(value));
                    } else if (explode) {
                        queryStringParameters = queryStringParameters.put(queryParamName, queryParameterValues.add(value));
                    } else {
                        throw new RuntimeException("serialized as if exploded but this is not supported");
                    }
                }
            } else if ("string".equals(type)) {
                queryStringParameters = queryStringParameters.put(queryParamName, value);
            } else if ("integer".equals(type)) {
                queryStringParameters = queryStringParameters.put(queryParamName, Long.valueOf(value));
            } else if ("number".equals(type)) {
                queryStringParameters = queryStringParameters.put(queryParamName, new BigDecimal(value));
            }
        }
        return queryStringParameters;
    }
}
