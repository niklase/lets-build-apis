package com.zuunr.dcentb.rest.processor.html;

import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValueFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginFormRendererTest {

    @Test
    @DisplayName("renders the login form, already final HTML, with no further processing needed")
    void rendersLoginForm() {
        JsonObject context = JsonObject.EMPTY.put("request", JsonObject.EMPTY.put("uri", "/ui/login?next=%2Ftodos"));

        JsonObject result = new LoginFormRenderer(JsonValueFactory.create("{}")).process(context);

        assertTrue(result.get("htmlAlreadyRendered").getBoolean());
        assertTrue(result.get("unrestrictedAccess").getBoolean());
        JsonObject response = result.get("response").getJsonObject();
        assertEquals(200, response.get("status").getInteger());
        assertEquals("text/html; charset=utf-8", response.get("headers").get("content-type").getJsonArray().get(0).getString());
        assertTrue(response.get("body").getString().contains("name=\"next\" value=\"/todos\""));
    }
}
