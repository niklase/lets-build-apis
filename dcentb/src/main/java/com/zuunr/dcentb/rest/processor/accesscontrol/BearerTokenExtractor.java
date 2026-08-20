package com.zuunr.dcentb.rest.processor.accesscontrol;

import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;

/**
 * Shared by every JWT-based Authenticator (JwtAuthenticator, SharedSecretJwtAuthenticator).
 */
final class BearerTokenExtractor {

    private BearerTokenExtractor() {
    }

    static String extract(JsonObject request) {
        JsonArray authorizationHeader = request.get("headers", JsonObject.EMPTY).get("authorization", JsonArray.EMPTY).getJsonArray();

        return authorizationHeader.stream()
                .map(JsonValue::getString)
                .filter(value -> value != null && value.regionMatches(true, 0, "Bearer ", 0, 7))
                .map(value -> value.substring(7).trim())
                .findFirst()
                .orElse(null);
    }
}
