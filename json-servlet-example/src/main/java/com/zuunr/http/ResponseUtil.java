package com.zuunr.http;

import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonObjectBuilder;
import com.zuunr.json.JsonValue;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class ResponseUtil {

    public static JsonObject createResponse(ResponseEntity<JsonValue> responseEntity) {
        JsonObjectBuilder builder = JsonObject.EMPTY.builder();
        builder.put("status", responseEntity.getStatusCode().value());
        JsonValue body = responseEntity.getBody();
        if (body != null) {
            builder.put("body", responseEntity.getBody());
        }
        builder.put("headers", createHeaders(responseEntity.getHeaders()));
        return builder.build();
    }

    public static JsonObject createHeaders(HttpHeaders headers) {
        JsonObjectBuilder builder = JsonObject.EMPTY.builder();

        Set<Map.Entry<String, List<String>>> entrySet = headers.entrySet();

        for (Map.Entry<String, List<String>> entry : entrySet) {
            builder.put(entry.getKey().toLowerCase(), JsonArray.of(entry.getValue().toArray()));
        }
        return builder.build();
    }


}
