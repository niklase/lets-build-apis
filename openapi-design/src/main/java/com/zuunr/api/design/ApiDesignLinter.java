package com.zuunr.api.design;

import com.zuunr.api.openapi.AdditionalPropertiesApplicator;
import com.zuunr.api.openapi.OAS3Deserializer;
import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonObjectBuilder;
import com.zuunr.json.JsonValue;
import com.zuunr.json.pointer.JsonPointer;
import com.zuunr.json.schema.JsonSchema;
import com.zuunr.json.schema.Keywords;

import java.net.URI;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


public class ApiDesignLinter {

    private static Pattern PATTERN = Pattern.compile("^[/]([^/]*)([/]([^/]*))?$");

    public static JsonObject validateApiDesign(JsonObject infoModel, JsonObject request) {

        URI requestUri = URI.create(request.get("uri").getString());

        String path = requestUri.getPath();
        String method = request.get("method").getString();

        Matcher matcher = PATTERN.matcher(path);
        if (matcher.find()) {
            String collectionName = matcher.group(1);
            String idOrOperation = matcher.group(3);
            try {
                JsonObject spec = createOpenApiPaths(method, collectionName, idOrOperation, infoModel);
                JsonObject deserializationResult = OAS3Deserializer.deserializeRequest(JsonObject.EMPTY.put("request", request), spec.get("operation").getJsonObject());
                return JsonObject.EMPTY
                        .put("ok", deserializationResult.get("ok"))
                        .put("deserializationResult", deserializationResult)
                        .put("openApiDocument", createOpenApiDocument(spec));
            } catch (RefNotFoundException e) {
                return JsonObject.EMPTY.put("ok", false).put("error", "There is no collection named '" + collectionName + "'");
            }
        } else {
            return JsonObject.EMPTY.put("ok", false).put("error", "Nothing matches method (" + method + ") and path (" + path + ")");
        }
    }

    private static JsonObject createOpenApiDocument(JsonObject spec) {
        return JsonObject.EMPTY.put(JsonArray.of("paths", spec.get("path"), spec.get("method")), spec.get("operation"));
    }


    public static JsonObject createOpenApiDocument(JsonObject openApiDocWithoutPaths, JsonArray collectionNames, JsonObject infoModel) {
        return openApiDocWithoutPaths
                .put("tags", createTags(collectionNames))
                .put("paths", createOpenApiPaths(collectionNames, infoModel));
    }

    public static JsonArray createTags(JsonArray collections) {
        List<JsonValue> list = collections.stream().map(s -> JsonObject.EMPTY.put("name", s).put("description", "...").jsonValue()).collect(Collectors.toList());
        return JsonArray.ofList(list);
    }

    public static JsonObject createOpenApiPaths(JsonArray collectionNames, JsonObject infoModel) {
        JsonObjectBuilder result = JsonObject.EMPTY.builder();
        for (JsonValue name : collectionNames) {
            result.putAll(createOpenApiPaths(name.getString(), infoModel));
        }
        return result.build();
    }

    public static JsonObject createOpenApiPaths(String collectionName, JsonObject infoModel) {

        JsonArray tags = JsonArray.of(collectionName);

        JsonObject itemOperations = JsonObject.EMPTY
                .put("get", generateOperationReadWithGet(collectionName, infoModel).put("tags", tags))
                .put("patch", generateOperationUpdateWithPatch(collectionName, infoModel).put("tags", tags))
                .put("delete", generateOperationRemoveWithDelete(collectionName, infoModel).put("tags", tags));

        return JsonObject.EMPTY
                .put(JsonArray.of("/" + collectionName + "/getCollection", "post"), generateOperationReadCollectionWithPost(collectionName, infoModel).put("tags", tags))
                .put(JsonArray.of("/" + collectionName, "get"), generateOperationReadCollectionWithGet(collectionName, infoModel).put("tags", tags))
                .put(JsonArray.of("/" + collectionName, "post"), generateOperationCreateWithPost(collectionName, infoModel).put("tags", tags))
                .put(JsonArray.of("/" + collectionName + "/{id}"), itemOperations);
    }

    private static JsonObject generateOperationReadWithGet(String collectionName, JsonObject infoModel) {
        JsonSchema entityModel = generateEntityModel(collectionName, infoModel);
        return JsonObject.EMPTY
                .put("parameters", JsonArray.of(ID_PATH_PARAM))
                .put("responses", JsonObject.EMPTY
                        .put("200", JsonObject.EMPTY
                                .put("description", "OK")
                                .put("content", JsonObject.EMPTY.put("application/json", JsonObject.EMPTY.put("schema", entityModel.asJsonValue())))

                        ));
    }

    private static JsonObject createOpenApiPaths(String method, String collectionName, String idOrOperation, JsonObject infoModel) {

        if (idOrOperation != null) {
            if (idOrOperation.equals("getCollection")) {
                if (method.equals("post")) {
                    return generateReadCollectionWithPost(collectionName, infoModel);
                }
            } else {
                if (method.equals("patch")) {
                    return generateUpdateWithPatch(collectionName, infoModel);
                } else if (method.equals("delete")) {
                    return generateRemoveWithDelete(collectionName, infoModel);
                }
            }
        } else {
            if (method.equals("post")) {
                return generateCreateWithPost(collectionName, infoModel);
            } else if (method.equals("get")) {
                return generateReadCollectionWithGet(collectionName, infoModel);
            }
        }
        throw new RuntimeException("Unsupported combination of method and path");
    }

    private static JsonObject generateCreateWithPost(String collectionName, JsonObject infoModel) {
        return JsonObject.EMPTY
                .put("method", "post")
                .put("path", "/" + collectionName + "/{id}")
                .put("operation", generateOperationCreateWithPost(collectionName, infoModel));
    }

    private static JsonObject generateOperationCreateWithPost(String collectionName, JsonObject infoModel) {
        JsonSchema entityModelSchema = generateEntityModel(collectionName, infoModel);
        JsonObject entityModel = entityModelSchema.asJsonValue().getJsonObject();
        JsonObject requestModel = AdditionalPropertiesApplicator.updateAdditionalProperties(entityModel, JsonValue.FALSE);

        return JsonObject.EMPTY
                .put("requestBody", JsonObject.EMPTY
                        .put("required", true).put("content", JsonObject.EMPTY
                                .put("application/json", JsonObject.EMPTY
                                        .put("schema", requestModel))))
                .put("responses", JsonObject.EMPTY
                        .put("201", JsonObject.EMPTY
                                .put("description", "")
                                .put("content", JsonObject.EMPTY
                                        .put("application/json", JsonObject.EMPTY.put("schema", entityModel)))
                        )

                );
    }

    private static JsonObject generateRemoveWithDelete(String collectionName, JsonObject infoModel) {
        JsonSchema entityModel = generateEntityModel(collectionName, infoModel);
        return JsonObject.EMPTY
                .put("method", "delete")
                .put("delete", "/" + collectionName + "/{id}")
                .put("operation", generateOperationRemoveWithDelete(collectionName, infoModel));
    }

    private static JsonObject generateOperationRemoveWithDelete(String collectionName, JsonObject infoModel) {
        return JsonObject.EMPTY
                .put("parameters", JsonArray.of(ID_PATH_PARAM))
                .put("responses", JsonObject.EMPTY
                        .put("204", JsonObject.EMPTY
                                .put("description", "No content")));
    }

    private static final JsonObject ID_PATH_PARAM = JsonObject.EMPTY
            .put("schema", JsonObject.EMPTY
                    .put("type", "string"))
            .put("name", "id")
            .put("in", "path")
            .put("required", true);

    private static JsonObject generateUpdateWithPatch(String collectionName, JsonObject infoModel) {
        return JsonObject.EMPTY
                .put("method", "patch")
                .put("path", "/" + collectionName + "/{id}")
                .put("operation", generateOperationUpdateWithPatch(collectionName, infoModel));
    }

    private static JsonObject generateOperationUpdateWithPatch(String collectionName, JsonObject infoModel) {
        JsonSchema entityModelSchema = generateEntityModel(collectionName, infoModel);
        JsonObject entityModel = entityModelSchema.asJsonValue().getJsonObject();
        JsonObject requestModel = AdditionalPropertiesApplicator.updateAdditionalProperties(entityModel, JsonValue.FALSE);

        return JsonObject.EMPTY
                .put("parameters", JsonArray.of(ID_PATH_PARAM))
                .put("requestBody", JsonObject.EMPTY
                        .put("required", true).put("content", JsonObject.EMPTY
                                .put("application/json", JsonObject.EMPTY
                                        .put("schema", requestModel))))
                .put("responses", JsonObject.EMPTY
                        .put("200", JsonObject.EMPTY
                                .put("description", "OK")
                                .put("content", JsonObject.EMPTY
                                        .put("application/json", JsonObject.EMPTY
                                                .put("schema", entityModel)))

                        ));
    }

    private static JsonObject generateReadCollectionWithPost(String collectionName, JsonObject infoModel) {

        return JsonObject.EMPTY
                .put("method", "post")
                .put("path", "/" + collectionName + "/getCollection")
                .put("operation", generateOperationReadCollectionWithPost(collectionName, infoModel));
    }

    public static JsonObject generateOperationReadCollectionWithPost(String collectionName, JsonObject infoModel) {
        JsonSchema entityModel = generateEntityModel(collectionName, infoModel);
        JsonSchema collectionSchema = generateCollectionModel(collectionName, infoModel);
        JsonArray queryParameters = ParametersGenerator.createCollectionQueryParameters(entityModel.asJsonValue().getJsonObject());
        JsonSchema queryAsObjectSchema = ParamToObjSchemaTranslator.translate(queryParameters);

        return JsonObject.EMPTY
                .put("requestBody", JsonObject.EMPTY
                        .put("required", true)
                        .put("content", JsonObject.EMPTY
                                .put("application/json", JsonObject.EMPTY
                                        .put("schema", queryAsObjectSchema.asJsonValue()))))
                .put("responses", JsonObject.EMPTY
                        .put("200", JsonObject.EMPTY
                                .put("description", "OK")
                                .put("content", JsonObject.EMPTY
                                        .put("application/json", JsonObject.EMPTY.put("schema", collectionSchema.asJsonValue())))
                        ));
    }

    private static JsonObject generateReadCollectionWithGet(String collectionName, JsonObject infoModel) {
        return JsonObject.EMPTY
                .put("method", "get")
                .put("path", "/" + collectionName)
                .put("operation", generateOperationReadCollectionWithGet(collectionName, infoModel));
    }

    private static JsonObject generateOperationReadCollectionWithGet(String collectionName, JsonObject infoModel) {
        // Generate OpenAPI operation for read
        JsonSchema entityModel = generateEntityModel(collectionName, infoModel);
        JsonSchema collectionSchema = generateCollectionModel(collectionName, infoModel);
        JsonArray queryParameters = ParametersGenerator.createCollectionQueryParameters(entityModel.asJsonValue().getJsonObject());
        return JsonObject.EMPTY
                .put("parameters", queryParameters)
                .put("responses", JsonObject.EMPTY.put("200", JsonObject.EMPTY
                        .put("content", JsonObject.EMPTY
                                .put("application/json", JsonObject.EMPTY
                                        .put("schema", collectionSchema.asJsonValue())))))
                ;
    }

    private static JsonSchema generateEntityModel(String collectionName, JsonObject infoModel) {

        JsonPointer refPointer = JsonPointer.of("/$defs/" + collectionName + "/item");
        if (infoModel.get(refPointer) == null) {
            throw new RefNotFoundException(refPointer);
        }

        JsonSchema schema = infoModel.put(Keywords.REF, refPointer.getJsonPointerString()).as(JsonSchema.class);
        return SchemaPatcher.patch(schema, schema).asJsonValue().getJsonObject().remove(Keywords.DEFS).as(JsonSchema.class);
    }

    private static JsonSchema generateCollectionModel(String collectionName, JsonObject infoModel) {

        JsonPointer refPointer = JsonPointer.of("/$defs/" + collectionName + "/collection");
        if (infoModel.get(refPointer) == null) {
            throw new RefNotFoundException(refPointer);
        }

        JsonSchema schema = infoModel.put(Keywords.REF, refPointer.getJsonPointerString()).as(JsonSchema.class);
        return SchemaPatcher.patch(schema, schema).asJsonValue().getJsonObject().remove(Keywords.DEFS).as(JsonSchema.class);
    }


    private static class RefNotFoundException extends RuntimeException {

        final JsonPointer jsonPointer;

        public RefNotFoundException(JsonPointer jsonPointer) {
            super();
            this.jsonPointer = jsonPointer;
        }
    }
}