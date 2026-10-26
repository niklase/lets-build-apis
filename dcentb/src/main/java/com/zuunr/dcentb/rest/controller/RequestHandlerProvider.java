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
        // Synthesized *after* EffectiveOpenApiDoc: these are framework pages, not Mongo-backed
        // collections, so they must never go through its per-path "x-dcentb.collections.<name>.
        // dbSchema" validation - see html-ui-rendering-design.md sections 2 and 8.
        effectiveDocument = withHtmlUiPaths(effectiveDocument);
        effectiveDocument = effectiveDocument.as(EffectiveXUi.class).getJsonValue().getJsonObject();
        new ApiKeyProvisioner().provision(effectiveDocument);
        new JwtSecretProvisioner().provision(effectiveDocument);
        pathConfigMapper = setupPathConfigMapper(effectiveDocument);
    }

    private static final String[] HTML_UI_PATHS = {"/ui", "/ui/login", "/ui/logout"};

    /**
     * "/ui" (the dashboard), "/ui/login" and "/ui/logout" (sections 2 and 8). "/ui"'s
     * response schema carries its own "x-ui.properties" so EffectiveXUi completes it the
     * same as any declared path; the login/logout operations need no schema at all - their
     * handlers are hand-written pages that never go through ValueRenderer (see
     * Processor.HTML_ALREADY_RENDERED).
     */
    private static JsonObject withHtmlUiPaths(JsonObject document) {
        JsonObject paths = document.get("paths", JsonObject.EMPTY).getJsonObject();
        for (String reservedPath : HTML_UI_PATHS) {
            if (paths.containsKey(reservedPath)) {
                throw new IllegalStateException("\"" + reservedPath + "\" is reserved for the built-in HTML UI "
                        + "(see html-ui-rendering-design.md) and cannot also be declared as an API path.");
            }
        }

        JsonObject dashboardItemSchema = JsonObject.EMPTY
                .put("type", "object")
                .put("properties", JsonObject.EMPTY
                        .put("name", JsonObject.EMPTY.put("type", "string"))
                        .put("href", JsonObject.EMPTY.put("type", "string"))
                        .put("displayName", JsonObject.EMPTY.put("type", "object")));

        JsonObject dashboardResponseSchema = JsonObject.EMPTY
                .put("type", "object")
                .put("x-ui", JsonObject.EMPTY.put("properties", JsonArray.of(JsonObject.EMPTY
                        .put("name", "collections")
                        .put("displayName", JsonObject.EMPTY.put("en_US", "Collections")))))
                .put("properties", JsonObject.EMPTY
                        .put("collections", JsonObject.EMPTY.put("type", "array").put("items", dashboardItemSchema)));

        JsonObject dashboardOperation = JsonObject.EMPTY
                .put("summary", "Collections")
                .put("responses", JsonObject.EMPTY.put("200", JsonObject.EMPTY
                        .put("content", JsonObject.EMPTY.put("application/json", JsonObject.EMPTY.put("schema", dashboardResponseSchema)))));

        paths = paths
                .put("/ui", JsonObject.EMPTY.put("get", dashboardOperation.jsonValue()))
                .put("/ui/login", JsonObject.EMPTY
                        .put("get", JsonObject.EMPTY.put("summary", "Log in").jsonValue())
                        .put("post", JsonObject.EMPTY.put("summary", "Log in").jsonValue()))
                .put("/ui/logout", JsonObject.EMPTY.put("post", JsonObject.EMPTY.put("summary", "Log out").jsonValue()));

        return document.put("paths", paths.jsonValue());
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