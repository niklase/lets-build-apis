package com.zuunr.dcentb.rest.processor.html;

import com.zuunr.dcentb.rest.html.LoginPage;
import com.zuunr.dcentb.rest.html.NextParam;
import com.zuunr.dcentb.rest.html.SessionCookie;
import com.zuunr.dcentb.rest.processor.Processor;
import com.zuunr.dcentb.rest.processor.accesscontrol.ApiKeyAuthenticator;
import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * "POST /ui/login" - validates the submitted api-key by reusing ApiKeyAuthenticator
 * directly (zero duplicated Mongo-lookup logic): the form field is placed where that
 * processor already expects a credential (the "api-key" request header) and it is called
 * exactly as AuthenticationProcessor would. See html-ui-rendering-design.md section 8.
 * <p>
 * On success, sets the session cookie to the raw api-key itself - deliberate for v1: it's
 * the exact same secret already trusted today over a header, just carried differently, so
 * this adds no new trust surface (see the design doc for the v2 hardening note).
 */
public class LoginSubmitProcessor extends Processor {

    private final ApiKeyAuthenticator apiKeyAuthenticator;

    public LoginSubmitProcessor(JsonValue config) {
        super(config);
        this.apiKeyAuthenticator = config.as(ApiKeyAuthenticator.class);
    }

    @Override
    public JsonObject process(JsonObject requestContext) {
        JsonObject request = requestContext.get(REQUEST, JsonObject.EMPTY).getJsonObject();
        JsonValue rawBody = request.get("body");
        JsonObject form = parseForm(rawBody == null ? null : rawBody.getString());

        String apiKey = form.get("apiKey", JsonValue.NULL).getString();
        String next = form.get("next", JsonValue.NULL).getString();

        JsonObject syntheticRequest = request.put(JsonArray.of("headers", "api-key"), JsonArray.of(apiKey == null ? "" : apiKey));
        JsonObject authResult = apiKeyAuthenticator.process(JsonObject.EMPTY.put(REQUEST, syntheticRequest));

        if (authResult.get(RESPONSE) != null) {
            return renderedPage(requestContext, LoginPage.render(next, "Invalid API key"));
        }

        String location = NextParam.isSafeRedirectTarget(next) ? next : "/ui";
        JsonObject headers = JsonObject.EMPTY
                .put("location", JsonArray.of(location))
                .put("set-cookie", JsonArray.of(SessionCookie.NAME + "=" + apiKey + "; HttpOnly; Secure; SameSite=Lax; Path=/"));
        return requestContext
                .put(UNRESTRICTED_ACCESS, true)
                .put(HTML_ALREADY_RENDERED, true)
                .put(RESPONSE, JsonObject.EMPTY.put("status", 303).put("headers", headers));
    }

    private static JsonObject renderedPage(JsonObject requestContext, String html) {
        return requestContext
                .put(UNRESTRICTED_ACCESS, true)
                .put(HTML_ALREADY_RENDERED, true)
                .put(RESPONSE, JsonObject.EMPTY
                        .put("status", 200)
                        .put("headers", JsonObject.EMPTY.put("content-type", JsonArray.of("text/html; charset=utf-8")))
                        .put("body", html));
    }

    private static JsonObject parseForm(String rawBody) {
        JsonObject result = JsonObject.EMPTY;
        if (rawBody == null || rawBody.isEmpty()) {
            return result;
        }
        for (String pair : rawBody.split("&")) {
            int equalsIndex = pair.indexOf('=');
            if (equalsIndex <= 0) {
                continue;
            }
            result = result.put(decode(pair.substring(0, equalsIndex)), decode(pair.substring(equalsIndex + 1)));
        }
        return result;
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return value;
        }
    }
}
