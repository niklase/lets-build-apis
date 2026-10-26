package com.zuunr.dcentb.rest.processor.html;

import com.zuunr.dcentb.rest.html.CollectionTableRenderer;
import com.zuunr.dcentb.rest.html.ContentNegotiation;
import com.zuunr.dcentb.rest.html.Headers;
import com.zuunr.dcentb.rest.html.HtmlEscaper;
import com.zuunr.dcentb.rest.html.HtmlPage;
import com.zuunr.dcentb.rest.html.QueryString;
import com.zuunr.dcentb.rest.html.ValueRenderer;
import com.zuunr.dcentb.rest.processor.Processor;
import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * The "last processor" (html-ui-rendering-design.md section 1): invoked generically, for
 * every request handler, right after ResponseAccessController - see RequestHandlerBase. A
 * no-op unless the request negotiated for HTML (ContentNegotiation.wantsHtml); JSON API
 * behavior is completely unchanged otherwise.
 * <p>
 * A 401 becomes a redirect to the login page instead of a rendered page (section 8); 403/
 * 404 get a small hand-written page; any other non-2xx renders the error body through the
 * same schema-less ValueRenderer fallback used everywhere else. A 2xx body shaped
 * {"items": [...]} is a collection and gets a table (section 9's one special case);
 * anything else is a single value rendered through ValueRenderer directly.
 */
public class HtmlRenderingProcessor extends Processor {

    private final JsonObject config;

    public HtmlRenderingProcessor(JsonValue config) {
        super(config);
        this.config = config.getJsonObject();
    }

    @Override
    public JsonObject process(JsonObject requestContext) {
        JsonObject request = requestContext.get(REQUEST, JsonObject.EMPTY).getJsonObject();
        JsonObject requestHeaders = request.get("headers", JsonObject.EMPTY).getJsonObject();

        if (requestContext.get(HTML_ALREADY_RENDERED, JsonValue.FALSE).getBoolean()) {
            return requestContext;
        }
        if (!ContentNegotiation.wantsHtml(requestHeaders)) {
            return requestContext;
        }

        JsonObject response = requestContext.get(RESPONSE, JsonObject.EMPTY).getJsonObject();
        int status = response.get("status").getInteger();

        if (status == 401) {
            return requestContext.put(RESPONSE, redirectToLogin(request));
        }
        if (status >= 300 && status < 400) {
            return requestContext; // an existing redirect (e.g. from login) is complete already - never rewritten
        }

        String fragment = status >= 200 && status < 300
                ? renderSuccess(response, requestHeaders, request)
                : renderError(status, response, requestHeaders);

        boolean isHtmxRequest = Headers.first(requestHeaders, "hx-request") != null;
        String body = isHtmxRequest ? fragment : HtmlPage.fullPage(title(), fragment);

        JsonObject htmlResponse = JsonObject.EMPTY
                .put("status", status)
                .put("headers", JsonObject.EMPTY.put("content-type", JsonArray.of("text/html; charset=utf-8")))
                .put("body", body);
        return requestContext.put(RESPONSE, htmlResponse);
    }

    private JsonObject redirectToLogin(JsonObject request) {
        String originalUri = request.get("uri", JsonValue.NULL).getString();
        String next = originalUri == null ? "" : URLEncoder.encode(originalUri, StandardCharsets.UTF_8);
        return JsonObject.EMPTY
                .put("status", 303)
                .put("headers", JsonObject.EMPTY.put("location", JsonArray.of("/ui/login?next=" + next)));
    }

    private String renderError(int status, JsonObject response, JsonObject requestHeaders) {
        if (status == 403) {
            return errorFragment("Forbidden", "You don't have permission to view this.");
        }
        if (status == 404) {
            return errorFragment("Not found", "Nothing was found at this address.");
        }
        String body = ValueRenderer.renderValue(response.get("body"), null, null, componentSchemas(), requestHeaders);
        return errorFragment("Error", body);
    }

    private static String errorFragment(String title, String bodyHtml) {
        return "<h1>" + HtmlEscaper.escape(title) + "</h1>" + bodyHtml;
    }

    private String renderSuccess(JsonObject response, JsonObject requestHeaders, JsonObject request) {
        JsonValue body = response.get("body");
        int status = response.get("status").getInteger();
        JsonValue schema = responseSchemaFor(status);
        JsonObject componentSchemas = componentSchemas();

        JsonObject bodyObject = body == null ? null : body.getJsonObject();
        JsonValue itemsValue = bodyObject == null ? null : bodyObject.get("items");

        if (itemsValue != null && itemsValue.isJsonArray()) {
            JsonValue itemSchema = itemSchemaOf(schema, componentSchemas);
            String table = CollectionTableRenderer.renderTable(itemsValue.getJsonArray(), itemSchema, componentSchemas, requestHeaders);
            return table + paginationNav(request, itemsValue.getJsonArray().size());
        }

        return ValueRenderer.renderValue(body, schema, null, componentSchemas, requestHeaders);
    }

    /** "properties.items.items" - the array property named "items", then its own "items" (array-element) schema. */
    private JsonValue itemSchemaOf(JsonValue collectionSchema, JsonObject componentSchemas) {
        JsonObject resolved = com.zuunr.dcentb.rest.html.SchemaRef.resolve(collectionSchema, componentSchemas);
        return resolved == null ? null : resolved.get(JsonArray.of("properties", "items", "items"));
    }

    private JsonObject componentSchemas() {
        return config.get("components", JsonObject.EMPTY).getJsonObject().get("schemas", JsonObject.EMPTY).getJsonObject();
    }

    private JsonValue responseSchemaFor(int status) {
        return config.get(JsonArray.of("operation", "responses", String.valueOf(status), "content", "application/json", "schema"));
    }

    private String title() {
        String summary = config.get(JsonArray.of("operation", "summary"), JsonValue.NULL).getString();
        return summary != null ? summary : "dcentb";
    }

    /** "Next"/"Prev" only when the request named an explicit limit - see design doc section 6. */
    private String paginationNav(JsonObject request, int itemCount) {
        String uri = request.get("uri", JsonValue.NULL).getString();
        if (uri == null) {
            return "";
        }
        String path = QueryString.pathOf(uri);
        JsonObject queryParams = QueryString.parse(uri);

        String limitParam = queryParams.get("limit", JsonValue.NULL).getString();
        if (limitParam == null) {
            return "";
        }
        int limit;
        try {
            limit = Integer.parseInt(limitParam);
        } catch (NumberFormatException e) {
            return "";
        }
        int offset = 0;
        String offsetParam = queryParams.get("offset", JsonValue.NULL).getString();
        if (offsetParam != null) {
            try {
                offset = Integer.parseInt(offsetParam);
            } catch (NumberFormatException e) {
                offset = 0;
            }
        }

        StringBuilder nav = new StringBuilder("<nav class=\"pagination\">");
        if (offset > 0) {
            int prevOffset = Math.max(0, offset - limit);
            nav.append("<a href=\"").append(HtmlEscaper.escape(path)).append("?limit=").append(limit)
                    .append("&offset=").append(prevOffset).append("\">Prev</a>");
        }
        if (itemCount == limit) {
            nav.append("<a href=\"").append(HtmlEscaper.escape(path)).append("?limit=").append(limit)
                    .append("&offset=").append(offset + limit).append("\">Next</a>");
        }
        nav.append("</nav>");
        return nav.toString();
    }
}
