package com.zuunr.api.openapi;

import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;

import java.net.URI;

public class JsonUri {

    private final URI uri;
    private JsonValue path;
    private JsonValue scheme;
    private JsonValue host;
    private JsonValue port;
    private JsonValue query;

    private JsonObject multiValueQuery;


    public JsonUri(JsonValue jsonValue) {
        uri = URI.create(jsonValue.getString());
    }

    public JsonValue getPath() {
        if (path == null) {
            path = JsonValue.of(uri.getPath());
        }
        return path;
    }

    public JsonValue getScheme() {
        if (scheme == null) {
            scheme = JsonValue.of(uri.getScheme());
        }
        return scheme;
    }

    public JsonValue getHost() {
        if (host == null) {
            host = JsonValue.of(uri.getHost());
        }
        return host;
    }

    public JsonValue getPort() {
        if (port == null) {
            port = JsonValue.of(uri.getPort());
        }
        return port;
    }

    public JsonValue getQuery() {
        if (query == null) {
            query = JsonValue.of(uri.getQuery());
        }
        return query;
    }

    public JsonObject getMultiValueQuery() {
        if (multiValueQuery == null) {
            try {
                multiValueQuery = OAS3Deserializer.parseQueryStringToMultiValueJsonObject("queryString", JsonObject.EMPTY.put("request", JsonObject.EMPTY.put("queryString", getQuery())));
            } catch (JsonSchemaValidationException jsonSchemaValidationException) {
                multiValueQuery = JsonObject.EMPTY;
            }
        }
        return multiValueQuery;
    }
}
