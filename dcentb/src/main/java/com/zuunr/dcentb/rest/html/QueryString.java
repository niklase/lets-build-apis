package com.zuunr.dcentb.rest.html;

import com.zuunr.json.JsonObject;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/** A request's raw "uri" (as stored on the Request JsonObject) is path+query together, un-parsed. */
public final class QueryString {

    private QueryString() {
    }

    public static JsonObject parse(String uri) {
        JsonObject result = JsonObject.EMPTY;
        if (uri == null) {
            return result;
        }
        int questionMark = uri.indexOf('?');
        if (questionMark < 0) {
            return result;
        }
        String raw = uri.substring(questionMark + 1);
        if (raw.isEmpty()) {
            return result;
        }
        for (String pair : raw.split("&")) {
            int equalsIndex = pair.indexOf('=');
            if (equalsIndex <= 0) {
                continue;
            }
            String name = decode(pair.substring(0, equalsIndex));
            if (!result.containsKey(name)) {
                result = result.put(name, decode(pair.substring(equalsIndex + 1)));
            }
        }
        return result;
    }

    public static String pathOf(String uri) {
        if (uri == null) {
            return null;
        }
        int questionMark = uri.indexOf('?');
        return questionMark < 0 ? uri : uri.substring(0, questionMark);
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return value;
        }
    }
}
