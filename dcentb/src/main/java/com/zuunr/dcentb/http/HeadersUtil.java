package com.zuunr.dcentb.http;

import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonObjectBuilder;
import org.springframework.http.HttpHeaders;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class HeadersUtil {

    private HeadersUtil() {}

    /**
     * Lowercases every header name, matching RequestUtil.createRequest - the one real path
     * from an inbound HTTP request to a Request object - so header lookups elsewhere
     * (ApiKeyAuthenticator's "api-key", BearerTokenExtractor's "authorization", etc.) can
     * always assume lowercase regardless of what case a client actually sent.
     */
    public static JsonObject translateHttpHeaders(HttpHeaders httpHeaders) {

        JsonObjectBuilder builder = JsonObject.EMPTY.builder();
        for (Iterator<Map.Entry<String, List<String>>> iterator = httpHeaders.headerSet().iterator(); iterator.hasNext(); ) {
            Map.Entry<String, List<String>> entry = iterator.next();
            JsonArray values = JsonArray.of(entry.getValue().toArray()); // TODO: Iterate the list directly to avoid extra loop
            String headerName = entry.getKey().toLowerCase();
            builder.put(headerName, values);
        }
        return builder.build();
    }
}
