package com.zuunr.dcentb.rest.controller;

import com.zuunr.dcentb.rest.Request;
import com.zuunr.dcentb.rest.Response;
import com.zuunr.dcentb.rest.requesthandler.ReadCollectionRequestHandler;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValueFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;

@Component
public class RequestHandlerProvider {

    private ReadCollectionRequestHandler readCollectionRequestHandler;
    private PathConfigMapper pathConfigMapper;

    @Autowired
    public RequestHandlerProvider(@Value("classpath:example.openapi.json") Resource resource) throws IOException {
        this(JsonValueFactory.create(
                new String(
                        Files.readAllBytes(
                                resource.getFile().toPath())
                ))
                .getJsonObject());
    }

    public RequestHandlerProvider(JsonObject openApiDocument) {
        pathConfigMapper = setupPathConfigMapper(openApiDocument);
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
            return operationConfig.get("config").as(ReadCollectionRequestHandler.class);
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