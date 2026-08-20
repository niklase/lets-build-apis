package com.zuunr.dcentb.rest.processor.accesscontrol;

import com.auth0.jwk.Jwk;
import com.auth0.jwk.JwkException;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.JwkProviderBuilder;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.zuunr.dcentb.rest.processor.Processor;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import com.zuunr.json.JsonValueFactory;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

/**
 * Authenticates requests by verifying a Bearer JWT (RS256) against the public keys
 * published by an OpenID Connect issuer. This is the sub-processor AuthenticationProcessor
 * delegates to for any OAS "components.securitySchemes" entry of type "openIdConnect".
 *
 * The issuer's public keys are located via standard OIDC discovery (RFC-style
 * "openIdConnectUrl" -> discovery document -> "jwks_uri"), so this works with any
 * standards-compliant issuer (Auth0, Okta, Google Identity Platform, ...) without
 * provider-specific code.
 *
 * Configure as a security scheme:
 *   components:
 *     securitySchemes:
 *       Auth0:
 *         type: openIdConnect
 *         openIdConnectUrl: "https://{tenant}.auth0.com/.well-known/openid-configuration"
 *         x-dcentb:
 *           audience: "{expected-aud-claim}"   # optional; validated if present
 */
public class JwtAuthenticator extends Processor {

    private final String issuer;
    private final String audience;
    private final JwkProvider jwkProvider;

    public JwtAuthenticator(JsonValue config) {
        super(config);

        JsonObject scheme = config.getJsonObject();
        String type = scheme.get("type", JsonValue.NULL).getString();
        if (!"openIdConnect".equals(type)) {
            throw new IllegalArgumentException("JwtAuthenticator only supports securitySchemes of type 'openIdConnect', got: " + type);
        }

        String discoveryUrl = scheme.get("openIdConnectUrl", JsonValue.NULL).getString();
        if (discoveryUrl == null || discoveryUrl.isBlank()) {
            throw new IllegalArgumentException("openIdConnectUrl must be configured for an openIdConnect security scheme");
        }

        JsonObject discoveryDocument = fetchDiscoveryDocument(discoveryUrl);
        this.issuer = discoveryDocument.get("issuer", JsonValue.NULL).getString();
        String jwksUri = discoveryDocument.get("jwks_uri", JsonValue.NULL).getString();
        if (issuer == null || jwksUri == null) {
            throw new IllegalStateException("OIDC discovery document at " + discoveryUrl + " is missing 'issuer' or 'jwks_uri'");
        }

        this.audience = scheme.get(X_DCENTB, JsonObject.EMPTY).getJsonObject().get("audience", JsonValue.NULL).getString();
        this.jwkProvider = buildJwkProvider(jwksUri);
    }

    private static JsonObject fetchDiscoveryDocument(String discoveryUrl) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder(URI.create(discoveryUrl)).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Failed to fetch OIDC discovery document from " + discoveryUrl + ": HTTP " + response.statusCode());
            }
            return JsonValueFactory.create(response.body()).getJsonObject();
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException("Failed to fetch OIDC discovery document from " + discoveryUrl, e);
        }
    }

    private static JwkProvider buildJwkProvider(String jwksUri) {
        try {
            return new JwkProviderBuilder(new URL(jwksUri))
                    .cached(10, 24, TimeUnit.HOURS)
                    .rateLimited(10, 1, TimeUnit.MINUTES)
                    .build();
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Invalid jwks_uri from discovery document: " + jwksUri, e);
        }
    }

    @Override
    public JsonObject process(JsonObject requestContext) {
        JsonObject request = requestContext.get(REQUEST).getJsonObject();
        String token = BearerTokenExtractor.extract(request);

        if (token == null || token.isEmpty()) {
            return requestContext.put(RESPONSE, JsonObject.EMPTY.put("status", 401).put("message", "Missing bearer token"));
        }

        try {
            DecodedJWT unverified = JWT.decode(token);
            Jwk jwk = jwkProvider.get(unverified.getKeyId());
            Algorithm algorithm = Algorithm.RSA256((RSAPublicKey) jwk.getPublicKey(), null);

            var verification = JWT.require(algorithm).withIssuer(issuer);
            if (audience != null && !audience.isBlank()) {
                verification = verification.withAudience(audience);
            }

            DecodedJWT verified = verification.build().verify(token);

            String payloadJson = new String(Base64.getUrlDecoder().decode(verified.getPayload()), StandardCharsets.UTF_8);
            JsonObject authenticatedUser = JsonValueFactory.create(payloadJson).getJsonObject();

            return requestContext.put("authenticatedUser", authenticatedUser);
        } catch (JWTVerificationException | JwkException | IllegalArgumentException e) {
            return requestContext.put(RESPONSE, JsonObject.EMPTY.put("status", 401).put("message", "Invalid bearer token"));
        }
    }
}
