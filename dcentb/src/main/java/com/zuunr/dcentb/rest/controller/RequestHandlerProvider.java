package com.zuunr.dcentb.rest.controller;

import com.zuunr.dcentb.rest.Request;
import com.zuunr.dcentb.rest.Response;
import com.zuunr.dcentb.rest.requesthandler.MethodNotAllowedRequestHandler;
import com.zuunr.dcentb.rest.requesthandler.NotFoundRequestHandler;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import com.zuunr.json.JsonValueFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class RequestHandlerProvider {

    private static final RequestHandlerHandle _404_HANDLE = new RequestHandlerHandle(new NotFoundRequestHandler(), JsonObject.EMPTY);
    private static final RequestHandlerHandle _405_HANDLE = new RequestHandlerHandle(new MethodNotAllowedRequestHandler(), JsonObject.EMPTY);

    private PathConfigMapper pathConfigMapper;

    @Autowired
    public RequestHandlerProvider(
            @Value("${dcentb.openapi.file:classpath:demo.openapi.json}") Resource resource,
            @Value("${dcentb.mongodb.connection:mongodb://admin:adminpassword@localhost:27017/?authSource=admin}") String mongodbConnectionString,
            @Value("${dcentb.mongodb.db:}") String databaseName // TODO: This should not be default. This could be stated in the demo.openapi.json instead

    ) throws IOException {
        this(
                JsonValueFactory.create(new String(resource.getInputStream().readAllBytes())).getJsonObject(),
                JsonObject.EMPTY.put("connection", mongodbConnectionString).put("db", databaseName)
        );
    }

    public RequestHandlerProvider(JsonObject openApiDocument) {
        this(openApiDocument, JsonObject.EMPTY);
    }

    public RequestHandlerProvider(JsonObject openApiDocument, JsonObject mongoDbConfig) {
        openApiDocument = applyMongodbConfig(openApiDocument, mongoDbConfig);
        pathConfigMapper = setupPathConfigMapper(openApiDocument);
    }

    private static JsonObject applyMongodbConfig(JsonObject openApiDocument, JsonObject mongoConfig) {

        JsonObject globalMongoConfig = openApiDocument.get("x-dcentb", JsonObject.EMPTY).get("mongodb", JsonObject.EMPTY).getJsonObject();
        JsonValue globalConnection = globalMongoConfig.get("connection", JsonValue.NULL);
        JsonValue globalDb = globalMongoConfig.get("db", JsonValue.NULL);
        String connectionString = mongoConfig.get("connection", globalConnection).getString();
        String databaseName = mongoConfig.get("db", JsonValue.NULL).getString();
        if (databaseName == null || databaseName.isEmpty()) {
            databaseName = globalDb.getString();
        }

        JsonObject paths = openApiDocument.get("paths").getJsonObject();
        JsonObject updatedPaths = paths;
        for (String path : paths.keySet()) {
            JsonObject operations = paths.get(path).getJsonObject();
            JsonObject updatedOperations = operations;
            for (String method : operations.keySet()) {
                JsonObject operation = operations.get(method).getJsonObject();
                JsonValue xDcentb = operation.get("x-dcentb");
                if (xDcentb != null && xDcentb.getJsonObject() != null) {
                    JsonValue mongodb = xDcentb.getJsonObject().get("mongodb");
                    if (mongodb != null && mongodb.getJsonObject() != null) {
                        JsonObject updatedMongodb = mongodb.getJsonObject();
                        if (databaseName != null) {
                            updatedMongodb = updatedMongodb.put("db", databaseName);
                        }
                        if (connectionString != null) {
                            updatedMongodb = updatedMongodb.put("connection", connectionString);
                        }

                        JsonObject updatedXDcentb = xDcentb.getJsonObject().put("mongodb", updatedMongodb.jsonValue());
                        JsonObject updatedOperation = operation.put("x-dcentb", updatedXDcentb.jsonValue());
                        updatedOperations = updatedOperations.put(method, updatedOperation.jsonValue());
                    }
                }
            }
            updatedPaths = updatedPaths.put(path, updatedOperations.jsonValue());
        }
        return openApiDocument.put("paths", updatedPaths.jsonValue());
    }

    private static PathConfigMapper setupPathConfigMapper(JsonObject openApiDocument) {
        PathConfigMapper pathConfigMapper = new PathConfigMapper();
        JsonObject paths = openApiDocument.get("paths").getJsonObject();
        JsonObject openApiDocumentWithoutPaths = openApiDocument.remove("paths");
        for (String path : paths.keySet()) {
            JsonObject operationsPerMethod = paths.get(path).getJsonObject();
            for (String method : operationsPerMethod.keySet()) {
                pathConfigMapper.addConfigForPathAndMethod(
                        path,
                        method,
                        openApiDocumentWithoutPaths
                                .put("method", method)
                                .put("path", path)                                  // get anything from
                                .put("operation", operationsPerMethod.get(method))  // this JsonValue as
                                .jsonValue()                                        // cached if needed
                );
            }
        }
        return pathConfigMapper;
    }

    public RequestHandlerHandle getRequestHandlerHandle(JsonObject request) {
        return getRequestHandlerHandle(Request.of(request));
    }

    public RequestHandlerHandle getRequestHandlerHandle(Request request) {
        RequestHandlerHandle handle = getRequestHandlerHandle(request.getURI().getPath(), request.getMethod());
        return handle;
    }

    private RequestHandlerHandle getRequestHandlerHandle(String path, String method) {
        try {
            JsonObject operationConfigAndBicatch = pathConfigMapper.getConfig(path, method.toLowerCase());
            JsonValue operationConfigJsonValue = operationConfigAndBicatch.get("config");
            OperationConfig operationConfig = operationConfigJsonValue.as(OperationConfig.class);
            JsonObject pathParams = operationConfigAndBicatch.get("pathParams", JsonObject.EMPTY).getJsonObject();
            JsonObject bicatch = JsonObject.EMPTY.put("pathParameters", pathParams);
            return new RequestHandlerHandle(operationConfig.getRequestHandler(), bicatch);
        } catch (MethodNotFoundException e) {
            return _405_HANDLE;
        } catch (PathNotFoundException e) {
            return _404_HANDLE;
        }
    }
}