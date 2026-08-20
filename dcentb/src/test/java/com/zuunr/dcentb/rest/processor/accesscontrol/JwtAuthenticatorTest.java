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
 * Exercises JwtAuthenticator against a local OIDC issuer (see OidcTestIssuer):
 * discovery document -> jwks_uri -> RSA signature verification, end to end.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JwtAuthenticatorTest {

    private static final String AUDIENCE = "test-audience";

    private OidcTestIssuer issuer;
    private JwtAuthenticator authenticator;

    @BeforeAll
    void startIssuerAndAuthenticator() throws Exception {
        issuer = new OidcTestIssuer();

        JsonValue scheme = JsonObject.EMPTY
                .put("type", "openIdConnect")
                .put("openIdConnectUrl", issuer.discoveryUrl())
                .put("x-dcentb-audience", AUDIENCE)
                .jsonValue();

        authenticator = new JwtAuthenticator(scheme);
    }

    @AfterAll
    void stopIssuer() {
        issuer.close();
    }

    private JsonObject requestContextWithAuthorizationHeader(String headerValue) {
        JsonObject headers = headerValue == null
                ? JsonObject.EMPTY
                : JsonObject.EMPTY.put("authorization", JsonArray.of(headerValue));
        return JsonObject.EMPTY.put("request", JsonObject.EMPTY.put("headers", headers));
    }

    @Test
    void validBearerTokenIsAuthenticated() {
        String token = issuer.token("user-123", AUDIENCE, Instant.now().plusSeconds(60));

        JsonObject result = authenticator.process(requestContextWithAuthorizationHeader("Bearer " + token));

        assertNull(result.get("response"));
        assertEquals("user-123", result.get("authenticatedUser").getJsonObject().get("userId").getString());
    }

    @Test
    void missingAuthorizationHeaderIsRejected() {
        JsonObject result = authenticator.process(requestContextWithAuthorizationHeader(null));

        assertEquals(401, result.get("response").getJsonObject().get("status").getInteger());
    }

    @Test
    void expiredTokenIsRejected() {
        String token = issuer.token("user-123", AUDIENCE, Instant.now().minusSeconds(60));

        JsonObject result = authenticator.process(requestContextWithAuthorizationHeader("Bearer " + token));

        assertEquals(401, result.get("response").getJsonObject().get("status").getInteger());
    }

    @Test
    void wrongAudienceIsRejected() {
        String token = issuer.token("user-123", "some-other-audience", Instant.now().plusSeconds(60));

        JsonObject result = authenticator.process(requestContextWithAuthorizationHeader("Bearer " + token));

        assertEquals(401, result.get("response").getJsonObject().get("status").getInteger());
    }

    @Test
    void malformedTokenIsRejected() {
        JsonObject result = authenticator.process(requestContextWithAuthorizationHeader("Bearer not-a-jwt"));

        assertEquals(401, result.get("response").getJsonObject().get("status").getInteger());
    }
}
