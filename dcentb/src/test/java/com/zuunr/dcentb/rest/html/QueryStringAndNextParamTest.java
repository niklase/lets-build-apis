package com.zuunr.dcentb.rest.html;

import com.zuunr.json.JsonObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueryStringAndNextParamTest {

    @Test
    @DisplayName("QueryString.parse decodes values and QueryString.pathOf strips the query")
    void queryStringParsesAndDecodes() {
        JsonObject params = QueryString.parse("/todos?limit=10&next=%2Ftodos%2F123");

        assertEquals("10", params.get("limit").getString());
        assertEquals("/todos/123", params.get("next").getString());
        assertEquals("/todos", QueryString.pathOf("/todos?limit=10"));
    }

    @Test
    @DisplayName("no query string parses to an empty object")
    void noQueryStringIsEmpty() {
        assertEquals(JsonObject.EMPTY, QueryString.parse("/todos"));
    }

    @Test
    @DisplayName("NextParam round-trips a same-origin path from a login redirect")
    void nextParamRoundTripsSameOriginPath() {
        String next = NextParam.fromUri("/ui/login?next=%2Ftodos%2F123");

        assertEquals("/todos/123", next);
        assertTrue(NextParam.isSafeRedirectTarget(next));
    }

    @Test
    @DisplayName("an absolute or scheme-relative next is rejected as unsafe")
    void absoluteNextIsUnsafe() {
        assertFalse(NextParam.isSafeRedirectTarget("https://evil.example/phish"));
        assertFalse(NextParam.isSafeRedirectTarget("//evil.example/phish"));
        assertFalse(NextParam.isSafeRedirectTarget("javascript:alert(1)"));
        assertFalse(NextParam.isSafeRedirectTarget(null));
    }

    @Test
    @DisplayName("no next param at all is null, not an empty string")
    void noNextParamIsNull() {
        assertNull(NextParam.fromUri("/ui/login"));
    }
}
