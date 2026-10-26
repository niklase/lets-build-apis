package com.zuunr.dcentb.rest.html;

import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CookieHeaderParserTest {

    @Test
    @DisplayName("no Cookie header parses to an empty object")
    void noCookieHeaderIsEmpty() {
        assertEquals(JsonObject.EMPTY, CookieHeaderParser.parse(JsonObject.EMPTY));
    }

    @Test
    @DisplayName("multiple cookies are split and trimmed")
    void multipleCookiesAreSplit() {
        JsonObject headers = JsonObject.EMPTY.put("cookie", JsonArray.of("dcentb-html-session=abc123; other=xyz"));

        JsonObject parsed = CookieHeaderParser.parse(headers);

        assertEquals("abc123", parsed.get("dcentb-html-session").getString());
        assertEquals("xyz", parsed.get("other").getString());
    }

    @Test
    @DisplayName("a malformed pair with no '=' is skipped, not fatal")
    void malformedPairIsSkipped() {
        JsonObject headers = JsonObject.EMPTY.put("cookie", JsonArray.of("garbage; dcentb-html-session=abc123"));

        JsonObject parsed = CookieHeaderParser.parse(headers);

        assertEquals("abc123", parsed.get("dcentb-html-session").getString());
        assertNull(parsed.get("garbage"));
    }
}
