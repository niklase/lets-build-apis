package com.zuunr.dcentb.rest.controller;

import com.zuunr.dcentb.rest.Request;
import com.zuunr.dcentb.rest.Response;
import com.zuunr.dcentb.rest.requesthandler.CreateItemRequestHandler;
import com.zuunr.dcentb.rest.requesthandler.ReadCollectionRequestHandler;
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
        String databaseName = mongoConfig.get("db").getString();
        if (databaseName.isEmpty()) {
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
                        }if (connectionString != null) {
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
                        openApiDocumentWithoutPaths                                 // Makes it possible to
                                .put("path", path)                                  // get anything from
                                .put("operation", operationsPerMethod.get(method))  // this JsonValue as
                                .jsonValue()                                        // cached if needed
                );
            }
        }
        return pathConfigMapper;
    }

    public RequestHandler getRequestHandler(JsonObject request) {
        return getRequestHandler(Request.of(request));
    }

    public RequestHandler getRequestHandler(Request request) {
        return getRequestHandler(request.getURI().getPath(), request.getMethod());
    }

    private RequestHandler getRequestHandler(String path, String method) {
        try {
            JsonObject operationConfig = pathConfigMapper.getConfig(path, method.toLowerCase());
            JsonValue config = operationConfig.get("config");
            if ("post".equalsIgnoreCase(method)) {
                return config.as(CreateItemRequestHandler.class);
            }
            return config.as(ReadCollectionRequestHandler.class);
        } catch (MethodNotFoundException e) {
            return new RequestHandler() {
                @Override
                public Response process(Request request) {
                    return new Response(JsonObject.EMPTY.put("status", 405));
                }
            };
        } catch (PathNotFoundException e) {
            return new RequestHandler() {
                @Override
                public Response process(Request request) {
                    return new Response(JsonObject.EMPTY.put("status", 404));
                }
            };
        }
    }
}