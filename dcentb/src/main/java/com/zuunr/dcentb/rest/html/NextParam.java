package com.zuunr.dcentb.rest.html;

import com.zuunr.json.JsonValue;

/**
 * The "?next=" param carried through the login redirect (html-ui-rendering-design.md
 * section 8). Only a same-origin relative path is ever a safe redirect target - a login
 * link crafted with "next=https://evil.example" must not send a just-authenticated user
 * there. "//evil.example" (scheme-relative) and a literal ":" (a scheme, or
 * backslash-confusable host tricks) are rejected too.
 */
public final class NextParam {

    private NextParam() {
    }

    public static String fromUri(String uri) {
        JsonValue value = QueryString.parse(uri).get("next");
        return value == null ? null : value.getString();
    }

    public static boolean isSafeRedirectTarget(String next) {
        return next != null
                && next.startsWith("/")
                && !next.startsWith("//")
                && !next.startsWith("/\\")
                && !next.contains(":");
    }
}
