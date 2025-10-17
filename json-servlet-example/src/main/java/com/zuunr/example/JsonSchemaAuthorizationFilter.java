package com.zuunr.example;

import com.zuunr.api.openapi.OAS3Deserializer;
import com.zuunr.http.RequestUtil;
import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import com.zuunr.json.JsonValueFactory;
import com.zuunr.json.schema.JsonSchema;
import com.zuunr.json.schema.validation.JsonSchemaValidator;
import com.zuunr.rest.CachedBodyRequestWrapper;
import com.zuunr.rest.CachedBodyResponseWrapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNullApi;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

public class JsonSchemaAuthorizationFilter extends OncePerRequestFilter {

    private static final JsonObject DEFAULT_OAS_DESER_OP = JsonObject.EMPTY
            .put("requestBody", JsonObject.EMPTY.put("content", JsonObject.EMPTY.put("application/json", JsonObject.EMPTY.put("schema", JsonObject.EMPTY))))
            .put("parameters", JsonArray.EMPTY);

    private final PermissionSchemaProvider permissionSchemaProvider;
    private final RequestAccessController requestAccessController;

    public JsonSchemaAuthorizationFilter(
            @Autowired PermissionSchemaProvider permissionSchemaProvider,
            @Autowired RequestAccessController requestAccessController
    ) {
        this.permissionSchemaProvider = permissionSchemaProvider;
        this.requestAccessController = requestAccessController;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest servletRequest, HttpServletResponse servletResponse, FilterChain filterChain) throws ServletException, IOException {

        CachedBodyRequestWrapper requestWrapper = new CachedBodyRequestWrapper(servletRequest);
        CachedBodyResponseWrapper responseWrapper = new CachedBodyResponseWrapper(servletResponse);

        JsonObject request = RequestUtil.createRequest(servletRequest);

        JsonObject exchange = JsonObject.EMPTY.put("request", request);

        InputStream inputStream;

        inputStream = requestWrapper.getInputStream();

        exchange = OAS3Deserializer.deserializeRequest(exchange, inputStream, DEFAULT_OAS_DESER_OP);

        if (exchange.get("errors") != null) {
            servletResponse.setStatus(400);
            servletResponse.setContentType("application/json");
            servletResponse.getWriter().write(exchange.asJson());
            return;
        }

        exchange = exchange.put("request", request.putAll(exchange.get("request").getJsonObject()));

        try {
            JsonObject authorizedExchangeModel = requestAccessController.getAuthorizedExchange(exchange);
            filterChain.doFilter(
                    requestWrapper,
                    responseWrapper
            );

            JsonValue responseJsonBody = JsonValueFactory.create(new ByteArrayInputStream(responseWrapper.getDataStream()));
            if (responseJsonBody == null) {
                return;
            }

            JsonSchema responseSchema = permissionSchemaProvider.getResponsePermissionSchema(requestWrapper.getRequestURI(), requestWrapper.getMethod().toUpperCase(), "PERMISSION1").as(JsonSchema.class);
            JsonObject filteredResponse = filterResponse(authorizedExchangeModel.put(JsonArray.of("response", "body"), responseJsonBody), responseWrapper, responseSchema);
            if (filteredResponse == null) {
                return;
            }
            writeResponseBody(filteredResponse.get("body").asJson(), servletResponse);

        } catch (AuthenticationException authenticationException) {
            responseWrapper.setStatus(401);
            responseWrapper.setContentType("application/json");
            writeResponseBody(authenticationException.validation.asJson(), servletResponse);
        } catch (AuthorizationException authorizationException) {
            responseWrapper.setStatus(403);
            responseWrapper.setContentType("application/json");
            writeResponseBody(authorizationException.validation.asJson(), servletResponse);
        }
    }

    private void writeResponseBody(String responseBody, ServletResponse servletResponse) {
        final ServletOutputStream servletOutputStream;

        try {
            servletOutputStream = servletResponse.getOutputStream();
        } catch (Exception e) {
            throw new RuntimeException("servletResponse.getOutputStream() failed", e);
        }

        try {
            servletOutputStream.write(responseBody.getBytes());
        } catch (Exception e) {
            throw new RuntimeException("servletOutputStream.write(response.getBytes()) failed", e);
        }
    }


    private JsonObject filterResponse(JsonObject exchange, CachedBodyResponseWrapper responseWrapper, JsonSchema responseSchema) {
        JsonValue responseBody = JsonValueFactory.create(new String(responseWrapper.getDataStream()));
        JsonValue updatedExchange = new JsonSchemaValidator().filter(exchange.put(JsonArray.of("response", "body"), responseBody).jsonValue(), responseSchema);
        return updatedExchange.get("response").getJsonObject();
    }
}