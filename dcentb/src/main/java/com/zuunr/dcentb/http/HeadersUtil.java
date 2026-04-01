package com.zuunr.api.runtime.http;

import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonObjectBuilder;
import org.springframework.http.HttpHeaders;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class HeadersUtil {

    public static JsonObject translateHttpHeaders(HttpHeaders httpHeaders) {

        JsonObjectBuilder builder = JsonObject.EMPTY.builder();
        for (Iterator<Map.Entry<String, List<String>>> iterator = httpHeaders.entrySet().iterator(); iterator.hasNext(); ) {
            Map.Entry<String, List<String>> entry = iterator.next();
            JsonArray values = JsonArray.of(entry.getValue().toArray()); // TODO: Iterate the list directly to avoid extra loop
            String headerName = entry.getKey();
            builder.put(headerName, values);
        }
        return builder.build();
    }
}
