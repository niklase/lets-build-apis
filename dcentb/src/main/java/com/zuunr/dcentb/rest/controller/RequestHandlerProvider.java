package com.zuunr.dcentb.rest.controller;

import com.zuunr.dcentb.rest.Request;
import com.zuunr.dcentb.rest.processor.Processor;
import com.zuunr.dcentb.rest.processor.accesscontrol.ApiKeyProvisioner;
import com.zuunr.dcentb.rest.processor.accesscontrol.JwtSecretProvisioner;
import com.zuunr.dcentb.rest.requesthandler.basic.MethodNotAllowedRequestHandler;
import com.zuunr.dcentb.rest.requesthandler.basic.NotFoundRequestHandler;
import com.zuunr.json.JsonArray;
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
            @Value("${dcentb.mongodb.db:}") String databaseName

    ) throws IOException {
        this(
                JsonValueFactory.create(new String(resource.getInputStream().readAllBytes())).getJsonObject(),
                JsonObject.EMPTY
                        .put("connection", mongodbConnectionString)
                        .put("db", databaseName)
        );
    }

    public RequestHandlerProvider(JsonObject openApiDocument) {
        this(openApiDocument, JsonObject.EMPTY);
    }

    public RequestHandlerProvider(JsonObject openApiDocument, JsonObject mongoDbConfig) {
        JsonObject configuredDocument = applyExternalMongoConfig(openApiDocument, mongoDbConfig);
        JsonObject effectiveDocument = configuredDocument.as(EffectiveOpenApiDoc.class).getJsonValue().getJsonObject();
        new ApiKeyProvisioner().provision(effectiveDocument);
        new JwtSecretProvisioner().provision(effectiveDocument);
        pathConfigMapper = setupPathConfigMapper(effectiveDocument);
    }

    /**
     * Merges the externally-supplied (Spring property) connection string/database name
     * into the document's top-level "x-dcentb.mongodb", falling back to whatever the
     * document already declares there itself. This is deployment configuration, not a
     * default derived from the document's own structure - see {@link EffectiveOpenApiDoc}
     * for everything else (collection names, collectionSchemas).
     */
    private static JsonObject applyExternalMongoConfig(JsonObject openApiDocument, JsonObject mongoConfig) {

        JsonObject globalMongoConfig = openApiDocument.get("x-dcentb", JsonObject.EMPTY).get("mongodb", JsonObject.EMPTY).getJsonObject();
        JsonValue globalConnection = globalMongoConfig.get("connection", JsonValue.NULL);
        JsonValue globalDb = globalMongoConfig.get("db", JsonValue.NULL);
        String connectionString = mongoConfig.get("connection", globalConnection).getString();
        String databaseName = mongoConfig.get("db", JsonValue.NULL).getString();
        if (databaseName == null || databaseName.isEmpty()) {
            databaseName = globalDb.getString();
        }

        return openApiDocument
                .put(JsonArray.of(Processor.X_DCENTB, "mongodb", "connection"), connectionString)
                .put(JsonArray.of(Processor.X_DCENTB, "mongodb", "db"), databaseName);
    }

    private static PathConfigMapper setupPathConfigMapper(JsonObject openApiDocument) {
        PathConfigMapper pathConfigMapper = new PathConfigMapper();
        JsonObject paths = openApiDocument.get("paths").getJsonObject();
        JsonObject openApiDocumentWithoutPaths = openApiDocument.remove("paths");
        for (String path : paths.keySet()) {
            JsonObject operationsPerMethod = paths.get(path).getJsonObject();
            for (String method : operationsPerMethod.keySet()) {

                // Create a config that can be used for caching
                JsonObject config = openApiDocumentWithoutPaths
                        .put("method", method)
                        .put("path", path)
                        .put("operation", operationsPerMethod.get(method))
                        .put("components", openApiDocument.get("components", JsonObject.EMPTY).getJsonObject());

                pathConfigMapper.addConfigForPathAndMethod(
                        path,
                        method,
                        config.jsonValue()
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
            RequestHandlerConfig operationConfig = operationConfigJsonValue.as(RequestHandlerConfig.class);
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