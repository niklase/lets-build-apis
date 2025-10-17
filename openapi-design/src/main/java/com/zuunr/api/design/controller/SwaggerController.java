package com.zuunr.api.design.controller;

import com.zuunr.api.design.ApiDesignLinter;
import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import jdk.jfr.ContentType;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
public class SwaggerController {

    @PostMapping(value = "**")
    public Mono<ResponseEntity<String>> postAny(ServerHttpRequest serverHttpRequest) {
        return Mono.just(ResponseEntity.ok("hello"));
    }

    @GetMapping(value = "/swagger")
    public Mono<ResponseEntity<String>> getSwagger(ServerHttpRequest serverHttpRequest) {
        JsonObject openApiDoc = ApiDesignLinter.createOpenApiDocument(
                InMemDB.initialOpenApiDoc,
                InMemDB.infoModel.get("$defs", JsonObject.EMPTY).getJsonObject().remove("meta").keys().sort(),
                InMemDB.infoModel);
        return createSwagger(openApiDoc);
    }

    @PostMapping(value = "/swagger")
    public Mono<ResponseEntity<String>> postSwagger(ServerHttpRequest serverHttpRequest) {
        Mono<JsonValue> jsonBodyMono = RequestBodyUtils.getBodyAsJsonValue(serverHttpRequest);
        return jsonBodyMono.flatMap(body -> createSwagger(body.getJsonObject()));
    }

    public Mono<ResponseEntity<String>> createSwagger(JsonObject infoModel) {

        String html = """
                <!DOCTYPE html>
                <html>
                                
                <head>
                <meta charset="UTF-8" />
                <title>Swagger</title>
                                
                <style>
                .hidden {
                      display: none;
                }
                                
                label {
                  margin-bottom: 10px;
                  display: block;
                }
                </style>
                <link rel="stylesheet" type="text/css" href="https://cdnjs.cloudflare.com/ajax/libs/swagger-ui/5.0.0/swagger-ui.css" />
                </head>
                                
                <body>
                                
                <h2>Explore API</h2>
                                
                <div id="swagger-ui"></div>
                                
                                
                <script src="https://cdnjs.cloudflare.com/ajax/libs/swagger-ui/5.0.0/swagger-ui-bundle.js"></script>
                <script src="https://cdnjs.cloudflare.com/ajax/libs/swagger-ui/5.0.0/swagger-ui-standalone-preset.js"></script>
                <script>
                                
                /// SWAGGER START
                const ui = SwaggerUIBundle({
                //spec: ##OPENAPI_SPEC##,
                url: '/openapi-document',
                dom_id: '#swagger-ui',
                presets: [
                SwaggerUIBundle.presets.apis,
                SwaggerUIStandalonePreset
                ],
                apisSorter: "alpha",
                operationsSorter: (a, b) => {
                    var order = { 'get': '0', 'post': '1', 'patch': '2', 'put': '3', 'delete': '4' };
                                
                    var aa = JSON.parse(JSON.stringify(a));
                    var bb = JSON.parse(JSON.stringify(b));
                                
                    console.log("aa.method: "+aa.method);
                    console.log("aa.path: "+aa.path);
                                
                    console.log("bb.method: "+bb.method);
                    console.log("bb.path: "+bb.path);
                                
                                
                    const pathCompare = aa.path.localeCompare(bb.path);
                    const methodCompare = order[aa.method].localeCompare(order[bb.method]);
                    if (methodCompare === 0) {
                        return pathCompare;
                    }
                    return methodCompare;
                  },
                requestInterceptor: (req) => {
                return req;
                }
                                
                });
                window.ui = ui;
                /// SWAGGER END
                                
                </script>
                                
                </body>
                """;

        return Mono.just(ResponseEntity.ok(html.replace("##OPENAPI_SPEC##", infoModel.asJson())));
    }

    @GetMapping (value = "/openapi-document", produces = MediaType.APPLICATION_JSON_VALUE )
    public Mono<ResponseEntity<String>> getOpenApiDocument(){
        JsonObject openApiDoc = ApiDesignLinter.createOpenApiDocument(
                InMemDB.initialOpenApiDoc,
                InMemDB.infoModel.get("$defs", JsonObject.EMPTY).getJsonObject().remove("meta").keys().sort(),
                InMemDB.infoModel);



        return Mono.just(ResponseEntity.ok(openApiDoc.asJson()));
    }
}
