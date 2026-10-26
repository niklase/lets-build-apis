package com.zuunr.dcentb.rest.html;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginPageTest {

    @Test
    @DisplayName("a safe next is carried through as a hidden field")
    void safeNextIsCarriedThrough() {
        String html = LoginPage.render("/todos/123", null);

        assertTrue(html.contains("name=\"next\" value=\"/todos/123\""));
    }

    @Test
    @DisplayName("an unsafe next is replaced with the dashboard, never echoed back")
    void unsafeNextIsReplaced() {
        String html = LoginPage.render("https://evil.example", null);

        assertFalse(html.contains("evil.example"));
        assertTrue(html.contains("name=\"next\" value=\"/ui\""));
    }

    @Test
    @DisplayName("an error message is shown and escaped")
    void errorMessageIsShownAndEscaped() {
        String html = LoginPage.render(null, "Invalid API key");

        assertTrue(html.contains("Invalid API key"));
    }

    @Test
    @DisplayName("no error message renders no error paragraph")
    void noErrorMessageRendersNothingExtra() {
        String html = LoginPage.render(null, null);

        assertFalse(html.contains("class=\"error\""));
    }
}
