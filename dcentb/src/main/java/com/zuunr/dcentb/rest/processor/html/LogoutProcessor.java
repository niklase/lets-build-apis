package com.zuunr.dcentb.rest.processor.html;

import com.zuunr.dcentb.rest.html.SessionCookie;
import com.zuunr.dcentb.rest.processor.Processor;
import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;

/** "POST /ui/logout" - clears the session cookie and redirects back to the login page. */
public class LogoutProcessor extends Processor {

    public LogoutProcessor(JsonValue config) {
        super(config);
    }

    @Override
    public JsonObject process(JsonObject requestContext) {
        JsonObject headers = JsonObject.EMPTY
                .put("location", JsonArray.of("/ui/login"))
                .put("set-cookie", JsonArray.of(SessionCookie.NAME + "=; HttpOnly; Secure; SameSite=Lax; Path=/; Max-Age=0"));
        return requestContext
                .put(UNRESTRICTED_ACCESS, true)
                .put(HTML_ALREADY_RENDERED, true)
                .put(RESPONSE, JsonObject.EMPTY.put("status", 303).put("headers", headers));
    }
}
