package com.zuunr.dcentb.rest.processor.accesscontrol;

import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Covers AuthenticationProcessor's dispatch/override logic (not scheme-specific
 * verification, which JwtAuthenticatorTest already covers): defaulting to every
 * declared securityScheme when an operation doesn't restrict them, an operation
 * narrowing that down via "x-dcentb.accessControl.securitySchemes", an empty override
 * meaning "no auth required", and multiple schemes being tried in order.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthenticationProcessorTest {

    private OidcTestIssuer primaryIssuer;
    private OidcTestIssuer secondaryIssuer;

    @BeforeAll
    void startIssuers() throws Exception {
        primaryIssuer = new OidcTestIssuer();
        secondaryIssuer = new OidcTestIssuer();
    }

    @AfterAll
    void stopIssuers() {
        primaryIssuer.close();
        secondaryIssuer.close();
    }

    private JsonObject securitySchemes() {
        return JsonObject.EMPTY
                .put("Primary", JsonObject.EMPTY.put("type", "openIdConnect").put("openIdConnectUrl", primaryIssuer.discoveryUrl()))
                .put("Secondary", JsonObject.EMPTY.put("type", "openIdConnect").put("openIdConnectUrl", secondaryIssuer.discoveryUrl()));
    }

    private JsonValue configWith(JsonObject operation) {
        return JsonObject.EMPTY
                .put("components", JsonObject.EMPTY.put("securitySchemes", securitySchemes()))
                .put("operation", operation)
                .jsonValue();
    }

    private static JsonObject operationRestrictedTo(String... schemeNames) {
        return JsonObject.EMPTY.put("x-dcentb", JsonObject.EMPTY
                .put("accessControl", JsonObject.EMPTY
                        .put("securitySchemes", JsonArray.of((Object[]) schemeNames))));
    }

    private JsonObject requestContextWithBearer(String token) {
        JsonObject headers = token == null
                ? JsonObject.EMPTY
                : JsonObject.EMPTY.put("authorization", JsonArray.of("Bearer " + token));
        return JsonObject.EMPTY.put("request", JsonObject.EMPTY.put("headers", headers));
    }

    @Test
    void triesEveryDeclaredSchemeWhenOperationDoesNotRestrict() {
        AuthenticationProcessor processor = new AuthenticationProcessor(configWith(JsonObject.EMPTY));

        String token = primaryIssuer.token("user-1", null, Instant.now().plusSeconds(60));
        JsonObject result = processor.process(requestContextWithBearer(token));

        assertNull(result.get("response"));
        assertEquals("user-1", result.get("authenticatedUser").getJsonObject().get("userId").getString());
    }

    @Test
    void operationCanRestrictToASubsetOfSchemes() {
        JsonObject operation = operationRestrictedTo("Secondary");
        AuthenticationProcessor processor = new AuthenticationProcessor(configWith(operation));

        // A token from Primary must be rejected: this operation only accepts Secondary.
        String primaryToken = primaryIssuer.token("user-1", null, Instant.now().plusSeconds(60));
        JsonObject rejected = processor.process(requestContextWithBearer(primaryToken));
        assertEquals(401, rejected.get("response").getJsonObject().get("status").getInteger());

        // A token from Secondary succeeds.
        String secondaryToken = secondaryIssuer.token("user-2", null, Instant.now().plusSeconds(60));
        JsonObject accepted = processor.process(requestContextWithBearer(secondaryToken));
        assertNull(accepted.get("response"));
        assertEquals("user-2", accepted.get("authenticatedUser").getJsonObject().get("userId").getString());
    }

    @Test
    void emptySchemeListMeansNoAuthRequired() {
        JsonObject operation = operationRestrictedTo();
        AuthenticationProcessor processor = new AuthenticationProcessor(configWith(operation));

        JsonObject result = processor.process(requestContextWithBearer(null));

        assertNull(result.get("response"));
        assertNull(result.get("authenticatedUser"));
    }

    @Test
    void triesSchemesInOrderUntilOneSucceeds() {
        JsonObject operation = operationRestrictedTo("Primary", "Secondary");
        AuthenticationProcessor processor = new AuthenticationProcessor(configWith(operation));

        // Token only valid for Secondary: Primary is tried first and fails, then Secondary succeeds.
        String secondaryToken = secondaryIssuer.token("user-3", null, Instant.now().plusSeconds(60));
        JsonObject result = processor.process(requestContextWithBearer(secondaryToken));

        assertNull(result.get("response"));
        assertEquals("user-3", result.get("authenticatedUser").getJsonObject().get("userId").getString());
    }
}
