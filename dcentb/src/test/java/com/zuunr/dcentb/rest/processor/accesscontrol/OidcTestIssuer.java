package com.zuunr.dcentb.rest.processor.accesscontrol;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.sun.net.httpserver.HttpServer;
import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;

import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;

/**
 * A minimal local OIDC issuer for tests: serves a discovery document and a JWKS
 * document over a real localhost HttpServer (JDK built-in, no mocking library), and
 * signs tokens with the matching private key. Lets JwtAuthenticator/AuthenticationProcessor
 * be exercised end to end without any real Firebase/Auth0/etc. dependency.
 */
class OidcTestIssuer implements AutoCloseable {

    private static final String KEY_ID = "test-key-1";

    private final HttpServer server;
    private final String issuer;
    private final RSAPublicKey publicKey;
    private final RSAPrivateKey privateKey;

    OidcTestIssuer() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        this.publicKey = (RSAPublicKey) keyPair.getPublic();
        this.privateKey = (RSAPrivateKey) keyPair.getPrivate();

        this.server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        this.issuer = "http://localhost:" + server.getAddress().getPort();

        String jwks = JsonObject.EMPTY.put("keys", JsonArray.of(
                JsonObject.EMPTY
                        .put("kty", "RSA")
                        .put("use", "sig")
                        .put("alg", "RS256")
                        .put("kid", KEY_ID)
                        .put("n", encodeUnsigned(publicKey.getModulus()))
                        .put("e", encodeUnsigned(publicKey.getPublicExponent()))
        )).asJson();
        serveJson("/.well-known/jwks.json", jwks);

        String discoveryDocument = JsonObject.EMPTY
                .put("issuer", issuer)
                .put("jwks_uri", issuer + "/.well-known/jwks.json")
                .asJson();
        serveJson("/.well-known/openid-configuration", discoveryDocument);

        server.start();
    }

    private void serveJson(String path, String body) {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        server.createContext(path, exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream responseBody = exchange.getResponseBody()) {
                responseBody.write(bytes);
            }
        });
    }

    private static String encodeUnsigned(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            bytes = Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    String issuer() {
        return issuer;
    }

    String discoveryUrl() {
        return issuer + "/.well-known/openid-configuration";
    }

    String token(String userId, String audience, Instant expiresAt) {
        var builder = JWT.create()
                .withKeyId(KEY_ID)
                .withIssuer(issuer)
                .withClaim("userId", userId)
                .withExpiresAt(Date.from(expiresAt));
        if (audience != null) {
            builder = builder.withAudience(audience);
        }
        return builder.sign(Algorithm.RSA256(publicKey, privateKey));
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
