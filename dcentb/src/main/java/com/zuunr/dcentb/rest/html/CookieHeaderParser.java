package com.zuunr.dcentb.rest.html;

import com.zuunr.json.JsonObject;

/**
 * Parses the raw "Cookie" request header ("name1=value1; name2=value2") into individual
 * name/value pairs - nothing in the codebase touched cookies before the HTML UI's
 * cookie-based session (html-ui-rendering-design.md section 8).
 */
public final class CookieHeaderParser {

    private CookieHeaderParser() {
    }

    public static JsonObject parse(JsonObject requestHeaders) {
        String raw = Headers.first(requestHeaders, "cookie");
        JsonObject result = JsonObject.EMPTY;
        if (raw == null) {
            return result;
        }
        for (String pair : raw.split(";")) {
            String trimmed = pair.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int equalsIndex = trimmed.indexOf('=');
            if (equalsIndex <= 0) {
                continue;
            }
            String name = trimmed.substring(0, equalsIndex).trim();
            String value = trimmed.substring(equalsIndex + 1).trim();
            result = result.put(name, value);
        }
        return result;
    }
}
