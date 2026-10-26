package com.zuunr.dcentb.rest.processor.html;

import com.zuunr.dcentb.rest.html.LoginPage;
import com.zuunr.dcentb.rest.html.NextParam;
import com.zuunr.dcentb.rest.processor.Processor;
import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;

/**
 * "GET /ui/login" - unauthenticated by construction (no AuthenticationProcessor in this
 * handler's chain at all). See html-ui-rendering-design.md section 8.
 */
public class LoginFormRenderer extends Processor {

    public LoginFormRenderer(JsonValue config) {
        super(config);
    }

    @Override
    public JsonObject process(JsonObject requestContext) {
        JsonObject request = requestContext.get(REQUEST, JsonObject.EMPTY).getJsonObject();
        String next = NextParam.fromUri(request.get("uri", JsonValue.NULL).getString());
        String html = LoginPage.render(next, null);

        return requestContext
                .put(UNRESTRICTED_ACCESS, true)
                .put(HTML_ALREADY_RENDERED, true)
                .put(RESPONSE, JsonObject.EMPTY
                        .put("status", 200)
                        .put("headers", JsonObject.EMPTY.put("content-type", JsonArray.of("text/html; charset=utf-8")))
                        .put("body", html));
    }
}
