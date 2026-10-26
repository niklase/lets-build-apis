package com.zuunr.dcentb.rest.html;

import com.zuunr.json.JsonObject;

import java.util.Locale;

/**
 * Whether a request should get an HTML response instead of the default JSON one - see
 * html-ui-rendering-design.md section 1 ("Trigger condition"). Deliberately a single,
 * shared, stateless predicate: ApiKeyAuthenticator's cookie fallback (section 8) gates on
 * the exact same check as the HTML renderer itself, and the two disagreeing would be a
 * real bug (a request could end up silently falling back to a 401 it shouldn't, or vice
 * versa).
 * <p>
 * Rule: HTML is only rendered when "text/html" (or "text/*") is explicitly present in
 * Accept, with a quality at or above "application/json" (or "application/*")'s. "Accept:
 * * / *" alone, or no Accept header at all, is the unchanged, existing behavior - plain
 * JSON - since that's what curl/Postman/every existing integration test sends. A
 * browser's own default navigation Accept header lists "text/html" explicitly and first,
 * so plain &lt;a href&gt; navigation works with no special handling needed.
 */
public final class ContentNegotiation {

    private ContentNegotiation() {
    }

    public static boolean wantsHtml(JsonObject requestHeaders) {
        String accept = Headers.first(requestHeaders, "accept");
        if (accept == null || accept.isBlank()) {
            return false;
        }

        Double htmlQuality = bestQualityFor(accept, "text", "html");
        if (htmlQuality == null) {
            return false;
        }
        Double jsonQuality = bestQualityFor(accept, "application", "json");
        return htmlQuality >= (jsonQuality == null ? 0.0 : jsonQuality);
    }

    /** Highest q-value among entries that explicitly name this type (an exact match, or a type-level wildcard) - a fully wildcard range never matches. */
    private static Double bestQualityFor(String acceptHeader, String type, String subtype) {
        Double best = null;
        for (String rangeEntry : acceptHeader.split(",")) {
            String[] parts = rangeEntry.trim().split(";");
            String[] rangeParts = parts[0].trim().toLowerCase(Locale.ROOT).split("/");
            if (rangeParts.length != 2 || !rangeParts[0].equals(type)) {
                continue;
            }
            if (!rangeParts[1].equals(subtype) && !rangeParts[1].equals("*")) {
                continue;
            }
            double quality = qualityOf(parts);
            if (best == null || quality > best) {
                best = quality;
            }
        }
        return best;
    }

    private static double qualityOf(String[] rangeEntryParts) {
        for (int i = 1; i < rangeEntryParts.length; i++) {
            String param = rangeEntryParts[i].trim();
            if (param.startsWith("q=")) {
                try {
                    return Double.parseDouble(param.substring(2).trim());
                } catch (NumberFormatException e) {
                    return 1.0;
                }
            }
        }
        return 1.0;
    }
}
