package com.zuunr.dcentb.rest.html;

import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DisplayNameResolverTest {

    private static JsonObject headersWithAcceptLanguage(String acceptLanguage) {
        return JsonObject.EMPTY.put("accept-language", JsonArray.of(acceptLanguage));
    }

    @Test
    @DisplayName("no displayName map falls back to the raw name")
    void noDisplayNameFallsBackToRawName() {
        assertEquals("title", DisplayNameResolver.resolve(JsonObject.EMPTY, null, "title"));
    }

    @Test
    @DisplayName("Accept-Language's hyphenated tag matches displayName's underscored key")
    void hyphenatedAcceptLanguageMatchesUnderscoredKey() {
        JsonValue displayName = JsonValue.of(JsonObject.EMPTY.put("sv_SE", "Namn").put("en_US", "Name"));

        String resolved = DisplayNameResolver.resolve(headersWithAcceptLanguage("sv-SE,en;q=0.8"), displayName, "name");

        assertEquals("Namn", resolved);
    }

    @Test
    @DisplayName("falls through to a lower-preference locale when the top one has no entry")
    void fallsThroughToLowerPreferenceLocale() {
        JsonValue displayName = JsonValue.of(JsonObject.EMPTY.put("en_US", "Name"));

        String resolved = DisplayNameResolver.resolve(headersWithAcceptLanguage("sv-SE,en-US;q=0.5"), displayName, "name");

        assertEquals("Name", resolved);
    }

    @Test
    @DisplayName("no match for any requested locale falls back to the raw name")
    void noMatchFallsBackToRawName() {
        JsonValue displayName = JsonValue.of(JsonObject.EMPTY.put("fr_FR", "Nom"));

        String resolved = DisplayNameResolver.resolve(headersWithAcceptLanguage("sv-SE,en-US"), displayName, "name");

        assertEquals("name", resolved);
    }

    @Test
    @DisplayName("preference order follows explicit q-values, not header order")
    void preferenceOrderFollowsQValues() {
        assertEquals(
                java.util.List.of("en_US", "sv_SE"),
                DisplayNameResolver.acceptLanguagePreferenceOrder("sv-SE;q=0.5,en-US;q=0.9"));
    }
}
