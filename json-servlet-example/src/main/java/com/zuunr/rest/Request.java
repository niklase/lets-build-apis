package com.zuunr.rest;

import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;

import java.net.URI;

/**
 * This is the Request as it seen from the client
 *
 * @author Niklas Eldberger
 */
public class Request<T> {

    private JsonObject me;

    private String method;
    private URI uri;
    private String query;
    private JsonObject queryParameters;
    private JsonObject queryParametersExploded;

    private Class<T> bodyClass;


    public static Request of(JsonObject me) {
        return new Request(me);
    }

    public Request(JsonObject me) {
        this.me = me;

    }

    public JsonValue getBody() {
        return me.get("body");
    }

    public JsonObject getHeaders() {
        return me.get("headers", JsonObject.EMPTY).getJsonObject();
    }

    public JsonArray getHeader(String headerName) {
        return getHeaders().get(headerName, JsonValue.NULL).getJsonArray();
    }

    public URI getURI() {
        if (uri == null) {
            uri = URI.create(me.get("uri").getString());
        }
        return uri;
    }

    public String getMethod() {
        if (method == null) {
            method = me.get("method").getString();
        }
        return method;
    }

    public JsonObject asJsonObject() {
        return me;
    }
}
