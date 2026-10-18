package com.zuunr.dcentb.rest.processor.accesscontrol;

import com.zuunr.dcentb.rest.processor.Processor;
import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;

import java.util.Set;

/**
 * Masks credential-bearing request headers to "*****" in a requestContext. Used by
 * AuthenticationProcessor on every return path, so no processor running after it ever sees
 * a raw credential - and by RequestHandlerBase's diagnostic logging, so the one
 * requestContext snapshot taken *before* AuthenticationProcessor has run (the only point a
 * raw credential can still be present) is never logged in clear text either.
 *
 * The header names here match exactly what ApiKeyAuthenticator and BearerTokenExtractor
 * read. Always lowercase, with no case-insensitive matching needed: RequestUtil.createRequest
 * force-lowercases every inbound header name unconditionally, regardless of HTTP version.
 */
public final class CredentialHeaderRedactor {

    private static final String REDACTED_VALUE = "*****";

    private static final Set<String> CREDENTIAL_HEADER_NAMES = Set.of("api-key", "authorization");

    private CredentialHeaderRedactor() {
    }

    public static JsonObject redact(JsonObject requestContext) {
        JsonObject headers = requestContext.get(Processor.REQUEST, JsonObject.EMPTY).getJsonObject()
                .get("headers", JsonObject.EMPTY).getJsonObject();
        for (String headerName : CREDENTIAL_HEADER_NAMES) {
            if (headers.get(headerName) != null) {
                requestContext = requestContext.put(
                        JsonArray.of(Processor.REQUEST, "headers", headerName),
                        JsonArray.of(REDACTED_VALUE));
            }
        }
        return requestContext;
    }
}
