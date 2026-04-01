package com.zuunr.dcentb.rest.controller;

import com.zuunr.dcentb.rest.Request;
import com.zuunr.dcentb.rest.processor.ReadCollectionRequestHandler;
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
        String fileContent = new String(Files.readAllBytes(resource.getFile().toPath()));
        JsonObject openApiDocument = JsonValueFactory.create(fileContent).getJsonObject();
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

    public RequestHandler getRequestHandler(Request request) {
        JsonObject operationConfig = pathConfigMapper.getConfig(request.getURI().getPath(), request.getMethod().toLowerCase());
        return operationConfig.get("config").as(ReadCollectionRequestHandler.class);
    }
}