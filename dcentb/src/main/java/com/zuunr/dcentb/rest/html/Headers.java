package com.zuunr.dcentb.rest.html;

import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;

/** Request headers are always lowercase keys mapped to a JsonArray of values - see RequestUtil. */
public final class Headers {

    private Headers() {
    }

    public static String first(JsonObject headers, String headerName) {
        JsonArray values = headers.get(headerName, JsonArray.EMPTY).getJsonArray();
        return values.isEmpty() ? null : values.get(0).getString();
    }
}
