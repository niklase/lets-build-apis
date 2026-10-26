package com.zuunr.dcentb.rest.processor.html;

import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import com.zuunr.json.JsonValueFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers HtmlRenderingProcessor as the generic "last processor" - see
 * html-ui-rendering-design.md sections 1, 6, 7, 8 and RequestHandlerBase's wiring of it.
 */
class HtmlRenderingProcessorTest {

    private static final JsonValue CONFIG = JsonValueFactory.create("""
            {
              "operation": {
                "summary": "List todos",
                "responses": {"200": {"content": {"application/json": {"schema": {
                  "type": "object",
                  "properties": {"items": {"type": "array", "items": {
                    "type": "object",
                    "x-ui": {"properties": [{"name": "title", "displayName": {"en_US": "Title"}}]}
                  }}}
                }}}}}
              },
              "components": {"schemas": {}}
            }
            """);

    private static JsonObject requestContext(String accept, boolean hxRequest, String uri, int status, JsonValue body) {
        JsonObject headers = JsonObject.EMPTY.put("accept", com.zuunr.json.JsonArray.of(accept));
        if (hxRequest) {
            headers = headers.put("hx-request", com.zuunr.json.JsonArray.of("true"));
        }
        return JsonObject.EMPTY
                .put("request", JsonObject.EMPTY.put("headers", headers).put("uri", uri))
                .put("response", JsonObject.EMPTY.put("status", status).put("body", body));
    }

    @Test
    @DisplayName("a request that didn't ask for HTML is left completely unchanged")
    void nonHtmlRequestIsUnchanged() {
        JsonObject context = requestContext("application/json", false, "/todos", 200,
                JsonValueFactory.create("{\"items\": []}"));

        JsonObject result = new HtmlRenderingProcessor(CONFIG).process(context);

        assertEquals(context, result);
    }

    @Test
    @DisplayName("an HTML request for a collection gets a full page with a table")
    void htmlCollectionRendersFullPageWithTable() {
        JsonObject context = requestContext("text/html", false, "/todos", 200,
                JsonValueFactory.create("{\"items\": [{\"title\": \"Buy oat milk\"}]}"));

        JsonObject result = new HtmlRenderingProcessor(CONFIG).process(context);
        JsonObject response = result.get("response").getJsonObject();

        assertEquals("text/html; charset=utf-8", response.get("headers").getJsonObject().get("content-type").getJsonArray().get(0).getString());
        String body = response.get("body").getString();
        assertTrue(body.contains("<!DOCTYPE html>"));
        assertTrue(body.contains("<table>"));
        assertTrue(body.contains("Buy oat milk"));
    }

    @Test
    @DisplayName("an htmx-triggered request gets just the fragment, no full page shell")
    void htmxRequestGetsFragmentOnly() {
        JsonObject context = requestContext("text/html", true, "/todos", 200,
                JsonValueFactory.create("{\"items\": []}"));

        JsonObject result = new HtmlRenderingProcessor(CONFIG).process(context);

        String body = result.get("response").getJsonObject().get("body").getString();
        assertFalse(body.contains("<!DOCTYPE html>"));
    }

    @Test
    @DisplayName("a 401 becomes a redirect to the login page, not a rendered page")
    void unauthorizedRedirectsToLogin() {
        JsonObject context = requestContext("text/html", false, "/todos/123", 401, JsonValue.NULL);

        JsonObject result = new HtmlRenderingProcessor(CONFIG).process(context);
        JsonObject response = result.get("response").getJsonObject();

        assertEquals(303, response.get("status").getInteger());
        assertEquals("/ui/login?next=%2Ftodos%2F123",
                response.get("headers").getJsonObject().get("location").getJsonArray().get(0).getString());
    }

    @Test
    @DisplayName("a 403 renders a small forbidden page")
    void forbiddenRendersSmallPage() {
        JsonObject context = requestContext("text/html", false, "/todos", 403, JsonValue.NULL);

        JsonObject result = new HtmlRenderingProcessor(CONFIG).process(context);

        String body = result.get("response").getJsonObject().get("body").getString();
        assertTrue(body.contains("Forbidden"));
    }

    @Test
    @DisplayName("a single item (not a collection) renders through the generic object view, no table")
    void singleItemRendersAsObject() {
        JsonObject context = requestContext("text/html", true, "/todos/123", 200,
                JsonValueFactory.create("{\"title\": \"Buy oat milk\"}"));

        JsonObject result = new HtmlRenderingProcessor(CONFIG).process(context);

        String body = result.get("response").getJsonObject().get("body").getString();
        assertTrue(body.contains("<dl>"));
        assertFalse(body.contains("<table>"));
    }
}
