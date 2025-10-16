package com.zuunr.formbuilder.http;

import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonObjectBuilder;
import com.zuunr.json.JsonValue;
import jakarta.servlet.http.HttpServletRequest;

import java.net.URI;

public class RestRequest {

    private JsonObject self;
    private URI uri;

    public RestRequest(JsonValue jsonValue) {
        self = jsonValue.getJsonObject();
    }

    public RestRequest(HttpServletRequest request) {
        this(request, null);
    }

    public RestRequest(HttpServletRequest request, JsonValue body) {

        try {
            JsonObjectBuilder builder = JsonObject.EMPTY.builder();
            builder.put("method", request.getMethod());
            String query = request.getQueryString();
            builder.put("query", query);
            String path = request.getRequestURI();
            builder.put("path", path);
            String uri = path + (query != null ? "?" + query : "");
            builder.put("uri", uri);
            if (body != null) {
                builder.put("body", body);
            }
            self = builder.build();
        } catch (Exception e) {
            throw new RuntimeException();
        }
    }

    public JsonObject asJsonObject() {
        return self;
    }
}
