package com.zuunr.api.runtime.http;

import com.zuunr.dcentb.rest.Request;
import com.zuunr.dcentb.rest.Response;
import com.zuunr.json.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;

public class RequestUtil {


    public Request createRequest(HttpServletRequest serverHttpRequest) throws IOException {

        Enumeration<String> headerNames = serverHttpRequest.getHeaderNames();


        JsonObjectBuilder headersBuilder = JsonObject.EMPTY.builder();
        for (Iterator iter = headerNames.asIterator(); iter.hasNext(); ) {
            String headerName = iter.next().toString();
            JsonArrayBuilder headerValuesBuilder = JsonArray.EMPTY.builder();

            Enumeration headerValues = serverHttpRequest.getHeaders(headerName);
            for (Iterator iterValues = headerValues.asIterator(); iterValues.hasNext(); ) {
                headerValuesBuilder.add(iterValues.next().toString());
            }
            headersBuilder.put(headerName.toLowerCase(), headerValuesBuilder.build());
        }
        JsonObject headers = headersBuilder.build();


        String path = serverHttpRequest.getRequestURI();
        String queryString = serverHttpRequest.getQueryString();
        String uri = queryString == null || "".equals(queryString) ? path :  path + "?" + queryString;

        JsonObjectBuilder requestModelBuilder = JsonObject.EMPTY.builder()
                .put("method", serverHttpRequest.getMethod())
                .put("uri", uri)
                .put("headers", headers)
                .put("query", queryString);

        if (hasBody(serverHttpRequest)) {
            requestModelBuilder.put("body", createStringBody(serverHttpRequest));
        }
        return new Request(requestModelBuilder.build());
    }

    public ResponseEntity<String> createJsonResponseEntity(Response response) {
        return createResponseEntity(new Response(response.asJsonObject().put(JsonArray.of("headers", "content-type"), JsonArray.of("application/json"))));
    }

    public ResponseEntity<String> createResponseEntity(Response response) {

        ResponseEntity.BodyBuilder bodyBuilder = ResponseEntity.status(response.getStatus());
        bodyBuilder = bodyBuilder.headers(createHeaders(response.getHeaders()));

        if (response.getBody() == null) {
            return bodyBuilder.build();
        }

        if (response.getBody().isString()) {
            return bodyBuilder.body(response.getBody().getString());
        }

        //throw new RuntimeException("Response body should be string: " + response.getBody());
        return bodyBuilder.body(response.getBody().asJson());

    }

    public ResponseEntity<JsonValue> createResponseEntityJsonValue(Response response) {

        ResponseEntity.BodyBuilder bodyBuilder = ResponseEntity.status(response.getStatus());
        bodyBuilder = bodyBuilder.headers(createHeaders(response.getHeaders()));

        if (response.getBody() == null) {
            return bodyBuilder.build();
        }

        return bodyBuilder.body(response.getBody());

    }

    public HttpHeaders createHeaders(JsonObject headers) {
        JsonArray headerNames = headers.keys();
        JsonArray headerValues = headers.values();

        HttpHeaders httpHeaders = new HttpHeaders();
        for (int nameIndex = 0; nameIndex < headerNames.size(); nameIndex++) {

            JsonArray values = headerValues.get(nameIndex).getJsonArray();
            List<String> httpValues = new ArrayList(values.size());
            for (int valueIndex = 0; valueIndex < values.size(); valueIndex++) {
                httpValues.add(values.get(valueIndex).getString());
            }
            httpHeaders.addAll(headerNames.get(nameIndex).getString(), httpValues);
        }
        return httpHeaders;
    }

    public String createStringBody(HttpServletRequest request) throws IOException {

        StringBuilder body = new StringBuilder();
        try (BufferedReader reader = request.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line);
            }
        }
        return body.toString();
    }

    public static boolean hasBody(HttpServletRequest request) {
        // Check if Content-Length header is greater than 0
        String contentLengthHeader = request.getHeader("Content-Length");
        if (contentLengthHeader != null && Integer.parseInt(contentLengthHeader) > 0) {
            return true;
        }

        // Check if Transfer-Encoding header is present (e.g., chunked)
        String transferEncodingHeader = request.getHeader("Transfer-Encoding");
        if (transferEncodingHeader != null && !transferEncodingHeader.isEmpty()) {
            return true;
        }

        // No body found
        return false;
    }
}
