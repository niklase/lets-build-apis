package com.zuunr.dcentb.rest.processor.html;

import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValueFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogoutProcessorTest {

    @Test
    @DisplayName("clears the cookie (Max-Age=0) and redirects to the login page")
    void clearsCookieAndRedirects() {
        JsonObject result = new LogoutProcessor(JsonValueFactory.create("{}")).process(JsonObject.EMPTY);
        JsonObject response = result.get("response").getJsonObject();

        assertEquals(303, response.get("status").getInteger());
        assertEquals("/ui/login", response.get("headers").get("location").getJsonArray().get(0).getString());
        assertTrue(response.get("headers").get("set-cookie").getJsonArray().get(0).getString().contains("Max-Age=0"));
    }
}
