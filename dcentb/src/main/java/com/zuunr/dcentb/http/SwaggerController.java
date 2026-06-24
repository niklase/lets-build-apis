package com.zuunr.dcentb.http;

import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValueFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
public class SwaggerController {

    private final Resource openapiResource;

    public SwaggerController(@Value("${dcentb.openapi.file:classpath:demo.openapi.json}") Resource openapiResource) {
        this.openapiResource = openapiResource;
    }

    @GetMapping(value = "/swagger", produces = MediaType.TEXT_HTML_VALUE)
    public String swaggerUi() {
        return """
                <!DOCTYPE html>
                <html>
                  <head>
                    <title>API Docs</title>
                    <meta charset="utf-8"/>
                    <meta name="viewport" content="width=device-width, initial-scale=1">
                    <link rel="stylesheet" href="https://unpkg.com/swagger-ui-dist@5.18.2/swagger-ui.css">
                  </head>
                  <body>
                    <div id="swagger-ui"></div>
                    <script src="https://unpkg.com/swagger-ui-dist@5.18.2/swagger-ui-bundle.js"></script>
                    <script src="https://unpkg.com/swagger-ui-dist@5.18.2/swagger-ui-standalone-preset.js"></script>
                    <script>
                      SwaggerUIBundle({
                        url: "/swagger/openapi.json",
                        dom_id: "#swagger-ui",
                        deepLinking: true,
                        presets: [SwaggerUIBundle.presets.apis, SwaggerUIStandalonePreset],
                        plugins: [SwaggerUIBundle.plugins.DownloadUrl],
                        layout: "StandaloneLayout"
                      })
                    </script>
                  </body>
                </html>
                """;
    }

    @GetMapping(value = "/swagger/openapi.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public String openapiSpec() throws IOException {
        JsonObject spec = JsonValueFactory.create(
                new String(openapiResource.getInputStream().readAllBytes())
        ).getJsonObject();

        if (spec.get("info") == null) {
            spec = spec.put("info", com.zuunr.json.JsonObject.EMPTY
                    .put("title", "API")
                    .put("version", "1.0.0")
                    .jsonValue());
        }

        return spec.asJson();
    }
}