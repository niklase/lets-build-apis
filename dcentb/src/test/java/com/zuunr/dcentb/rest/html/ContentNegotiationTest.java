package com.zuunr.dcentb.rest.html;

import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContentNegotiationTest {

    private static JsonObject headersWithAccept(String accept) {
        return JsonObject.EMPTY.put("accept", JsonArray.of(accept));
    }

    @Test
    @DisplayName("no Accept header at all defaults to JSON")
    void noAcceptHeaderDefaultsToJson() {
        assertFalse(ContentNegotiation.wantsHtml(JsonObject.EMPTY));
    }

    @Test
    @DisplayName("\"*/*\" alone does not count as wanting HTML")
    void wildcardAloneIsJson() {
        assertFalse(ContentNegotiation.wantsHtml(headersWithAccept("*/*")));
    }

    @Test
    @DisplayName("a real browser's default navigation Accept header renders HTML")
    void browserDefaultAcceptRendersHtml() {
        assertTrue(ContentNegotiation.wantsHtml(headersWithAccept(
                "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")));
    }

    @Test
    @DisplayName("application/json alone stays JSON")
    void plainJsonAcceptIsJson() {
        assertFalse(ContentNegotiation.wantsHtml(headersWithAccept("application/json")));
    }

    @Test
    @DisplayName("text/html preferred strictly over application/json renders HTML")
    void htmlPreferredOverJsonRendersHtml() {
        assertTrue(ContentNegotiation.wantsHtml(headersWithAccept("text/html, application/json;q=0.8")));
    }

    @Test
    @DisplayName("application/json preferred strictly over text/html stays JSON")
    void jsonPreferredOverHtmlStaysJson() {
        assertFalse(ContentNegotiation.wantsHtml(headersWithAccept("text/html;q=0.5, application/json")));
    }

    @Test
    @DisplayName("equal preference favors HTML, since it was explicitly asked for")
    void equalPreferenceFavorsHtml() {
        assertTrue(ContentNegotiation.wantsHtml(headersWithAccept("text/html, application/json")));
    }

    @Test
    @DisplayName("\"text/*\" counts as explicitly wanting HTML")
    void textWildcardCountsAsHtml() {
        assertTrue(ContentNegotiation.wantsHtml(headersWithAccept("text/*")));
    }
}
