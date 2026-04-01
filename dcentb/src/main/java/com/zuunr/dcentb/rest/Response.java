package com.zuunr.dcentb.rest;

import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;

/**
 * @author Niklas Eldberger
 */
public class Response<T> {

    private JsonObject me;

    private Integer status;
    private JsonObject headers;
    private JsonValue body;

    private Class<T> _2xxBodyClass;

    public static Response<?> create(int status, JsonValue body) {
        return new Response<>(JsonObject.EMPTY.put("status", status).put("body", body));
    }

    public static <T> Response<T> create(int status, JsonValue body, Class<T> bodyClass) {
        return new Response<T>(status, null, body, bodyClass);
    }

    public static <T> Response<T> create(int status, JsonObject headers, JsonValue body, Class<T> bodyClass) {
        return new Response<T>(status, headers, body, bodyClass);
    }

    public static Response<?> create(int status, JsonObject body) {
        return create(status, body.jsonValue());
    }

    public static Response<?> create(JsonObject responseWithStatus) {
        return new Response<>(responseWithStatus);
    }

    public static Response<?> create(int status) {
        return new Response<>(JsonObject.EMPTY.put("status", status));
    }

    private Response(int status, JsonObject headers, JsonValue body, Class<T> _2xxBodyClass) {
        this.status = status;
        this._2xxBodyClass = _2xxBodyClass;

        JsonObject asJsonObject = JsonObject.EMPTY
                .put("status", status);
        asJsonObject = headers == null ? asJsonObject : asJsonObject.put("headers", headers);
        asJsonObject = body == null ? asJsonObject : asJsonObject.put("body", body);

        me = asJsonObject;
    }

    public Response(JsonObject me) {
        this.me = me;
    }

    public int getStatus() {
        if (status == null) {
            status = me.get("status").getJsonNumber().intValue();
        }
        return status;
    }

    public JsonObject getHeaders() {
        if (headers == null) {
            headers = me.get("headers", JsonObject.EMPTY).getJsonObject();
        }
        return headers;
    }

    public JsonValue getBody() {
        if (body == null) {
            body = me.get("body");
        }
        return body;
    }

    public T get2xxBody() {
        if (status != null && status > 199 && status < 300) {
            return getBody().as(_2xxBodyClass);
        }
        throw new RuntimeException("Status is " + status + " and there is no '2XX' body");
    }

    public JsonObject asJsonObject() {
        return me;
    }

    @Override
    public String toString() {
        return me.asJson();
    }
}
