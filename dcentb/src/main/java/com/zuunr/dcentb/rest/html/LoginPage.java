package com.zuunr.dcentb.rest.html;

/**
 * The one hand-written, non-schema-driven page in the whole HTML UI - see
 * html-ui-rendering-design.md section 8. Not routed through ValueRenderer/x-ui at all:
 * there is no JSON resource behind a login form.
 */
public final class LoginPage {

    private LoginPage() {
    }

    public static String render(String next, String errorMessage) {
        String safeNext = next != null && NextParam.isSafeRedirectTarget(next) ? next : "/ui";
        String errorHtml = errorMessage == null ? "" : "<p class=\"error\">" + HtmlEscaper.escape(errorMessage) + "</p>";
        String fragment = "<h1>Log in</h1>"
                + errorHtml
                + "<form method=\"post\" action=\"/ui/login\">"
                + "<input type=\"hidden\" name=\"next\" value=\"" + HtmlEscaper.escape(safeNext) + "\">"
                + "<label>API key<br><input type=\"password\" name=\"apiKey\" autofocus required></label>"
                + "<p><button type=\"submit\">Log in</button></p>"
                + "</form>";
        return HtmlPage.fullPage("Log in", fragment);
    }
}
