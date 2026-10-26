package com.zuunr.dcentb.rest.html;

import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolves a property/collection "displayName" locale map (html-ui-rendering-design.md
 * section 3) against a request's Accept-Language header. Accept-Language arrives
 * hyphenated per RFC ("sv-SE"); displayName's own keys are underscored, Java-Locale-style
 * ("sv_SE") - normalized here, the one place that mismatch is bridged. No entry, or no
 * match for any requested locale, falls back to the raw property/collection name.
 */
public final class DisplayNameResolver {

    private DisplayNameResolver() {
    }

    public static String resolve(JsonObject requestHeaders, JsonValue displayNameMap, String fallback) {
        if (displayNameMap == null || !displayNameMap.isJsonObject()) {
            return fallback;
        }
        JsonObject map = displayNameMap.getJsonObject();
        for (String locale : acceptLanguagePreferenceOrder(Headers.first(requestHeaders, "accept-language"))) {
            JsonValue value = map.get(locale);
            if (value != null && value.isString()) {
                return value.getString();
            }
        }
        return fallback;
    }

    /** Locale tags from Accept-Language, highest quality first, hyphens normalized to underscores. */
    static List<String> acceptLanguagePreferenceOrder(String acceptLanguage) {
        List<Entry> entries = new ArrayList<>();
        if (acceptLanguage == null || acceptLanguage.isBlank()) {
            return List.of();
        }
        int order = 0;
        for (String part : acceptLanguage.split(",")) {
            String[] pieces = part.trim().split(";");
            String tag = pieces[0].trim();
            if (tag.isEmpty()) {
                continue;
            }
            entries.add(new Entry(tag.replace('-', '_'), qualityOf(pieces), order++));
        }
        entries.sort((a, b) -> {
            int byQuality = Double.compare(b.quality, a.quality);
            return byQuality != 0 ? byQuality : Integer.compare(a.order, b.order);
        });

        List<String> result = new ArrayList<>(entries.size());
        for (Entry entry : entries) {
            result.add(entry.tag);
        }
        return result;
    }

    private static double qualityOf(String[] pieces) {
        for (int i = 1; i < pieces.length; i++) {
            String param = pieces[i].trim();
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

    private record Entry(String tag, double quality, int order) {
    }
}
