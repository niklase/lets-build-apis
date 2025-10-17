package com.zuunr.http;

import com.zuunr.api.http.HttpException;
import com.zuunr.json.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.util.Enumeration;
import java.util.Iterator;

public class RequestUtil {

    public static JsonObject createRequest(HttpServletRequest serverHttpRequest) {

        JsonObjectBuilder request = JsonObject.EMPTY.builder();
        JsonObject headers = createHeaders(serverHttpRequest);
        request.put("headers", headers);

        String path  = serverHttpRequest.getRequestURI();
        String queryString = serverHttpRequest.getQueryString();
        String uri = queryString == null || "".equals(queryString) ? path :  path + "?" + queryString;

        request.put("uri", uri);
        request.put("method", serverHttpRequest.getMethod());
        request.put("path", path);

        /*
        JsonValue body = createBody(serverHttpRequest.getHeader("content-type"), serverHttpRequest);
        if (body != null) {
            request.put("body", body);
        }

         */
        return request.build();
    }

    private static JsonObject createHeaders(HttpServletRequest serverHttpRequest) {

        JsonObjectBuilder headers = JsonObject.EMPTY.builder();
        Enumeration<String> headerNames = serverHttpRequest.getHeaderNames();

        for (Iterator<String> iterNames = headerNames.asIterator(); iterNames.hasNext(); ) {
            String name = iterNames.next();
            JsonArrayBuilder headerValues = JsonArray.EMPTY.builder();

            for (Enumeration<String> iterValues = serverHttpRequest.getHeaders(name); iterValues.hasMoreElements(); ) {
                String headerValue = iterValues.nextElement();
                headerValues.add(headerValue);
            }
            headers.put(name, headerValues.build());
        }
        return headers.build();
    }

    private static JsonValue createBody(String contentType, HttpServletRequest serverHttpRequest) throws HttpException {
        if (MediaType.parseMediaType(contentType).isCompatibleWith(MediaType.APPLICATION_JSON)) {
            try {
                return JsonValueFactory.create(serverHttpRequest.getInputStream());
            } catch (IOException ioException) {
                throw new HttpException(400);
            }
        } else {
            throw new HttpException(415);
        }
    }
}