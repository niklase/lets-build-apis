package com.zuunr.dcentb.rest.processor.accesscontrol;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import com.zuunr.mongodb.MongoJsonDB;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Reads (or creates, if absent) the shared secret SharedSecretJwtAuthenticator verifies
 * tokens with, then - purely for demo purposes - signs one JWT per entry of
 * "x-dcentb.jwtGeneration", so a demo (or a given-when-then test, see
 * DcentbGivenWhenThenTester) can exercise self-issued-JWT auth without a real
 * OIDC/Auth0/Firebase provider. Mirrors ApiKeyProvisioner.
 */
public class JwtSecretProvisioner {

    /**
     * Signs a token per "x-dcentb.jwtGeneration" entry and prints each to standard out.
     */
    public void provision(JsonObject openApiDocument) {
        JsonArray entries = jwtGenerationEntries(openApiDocument);
        if (entries.isEmpty()) {
            return;
        }

        Algorithm algorithm = Algorithm.HMAC256(resolveSecret(openApiDocument));

        for (int i = 0; i < entries.size(); i++) {
            JsonObject claims = entries.get(i).getJsonObject();
            String token = sign(claims, algorithm);
            System.out.println("[dcentb] jwt generated       claims=" + claims + "  token=" + token);
        }
    }

    /**
     * Signs a token per "x-dcentb.jwtGeneration" entry and returns them keyed by the
     * entry's "userId", without printing anything.
     */
    public JsonObject generateTokensByUserId(JsonObject openApiDocument) {
        JsonArray entries = jwtGenerationEntries(openApiDocument);
        if (entries.isEmpty()) {
            return JsonObject.EMPTY;
        }

        Algorithm algorithm = Algorithm.HMAC256(resolveSecret(openApiDocument));

        JsonObject tokensByUserId = JsonObject.EMPTY;
        for (int i = 0; i < entries.size(); i++) {
            JsonObject claims = entries.get(i).getJsonObject();
            String userId = claims.get("userId").getString();
            tokensByUserId = tokensByUserId.put(userId, sign(claims, algorithm));
        }
        return tokensByUserId;
    }

    private static String sign(JsonObject claims, Algorithm algorithm) {
        return JWT.create().withPayload(claims.asJson()).sign(algorithm);
    }

    private static JsonArray jwtGenerationEntries(JsonObject openApiDocument) {
        JsonValue jwtGenerationValue = openApiDocument.get(JsonArray.of("x-dcentb", "jwtGeneration"));
        JsonArray entries = jwtGenerationValue == null ? null : jwtGenerationValue.getJsonArray();
        return entries == null ? JsonArray.EMPTY : entries;
    }

    private String resolveSecret(JsonObject openApiDocument) {
        JsonObject mongoConfig = openApiDocument.get("x-dcentb", JsonObject.EMPTY).get("mongodb", JsonObject.EMPTY).getJsonObject();
        MongoJsonDB mongoJsonDB = mongoConfig.as(MongoJsonDB.class);
        return readOrCreateSecret(mongoJsonDB, resolveCollection(openApiDocument));
    }

    /**
     * The collection is a property of whichever "http"/"bearer" securityScheme is
     * declared (its "x-dcentb.jwtSecretCollection", same as SharedSecretJwtAuthenticator
     * reads) - not a document-global setting, since AuthenticationProcessor resolves it
     * per scheme too. Falls back to the plain default if no such scheme is declared.
     */
    private static String resolveCollection(JsonObject openApiDocument) {
        JsonObject securitySchemes = openApiDocument.get("components", JsonObject.EMPTY).getJsonObject()
                .get("securitySchemes", JsonObject.EMPTY).getJsonObject();
        for (String schemeName : securitySchemes.keySet()) {
            JsonObject scheme = securitySchemes.get(schemeName).getJsonObject();
            boolean isSharedSecretJwtScheme = "http".equals(scheme.get("type", JsonValue.NULL).getString())
                    && "bearer".equalsIgnoreCase(scheme.get("scheme", JsonValue.NULL).getString());
            if (isSharedSecretJwtScheme) {
                return scheme.get("x-dcentb", JsonObject.EMPTY).getJsonObject()
                        .get("jwtSecretCollection", SharedSecretJwtAuthenticator.DEFAULT_COLLECTION)
                        .getString();
            }
        }
        return SharedSecretJwtAuthenticator.DEFAULT_COLLECTION;
    }

    private String readOrCreateSecret(MongoJsonDB mongoJsonDB, String collection) {
        JsonObject findCommand = JsonObject.EMPTY.put("find", JsonObject.EMPTY
                .put("collection", collection)
                .put("filter", JsonObject.EMPTY
                        .put("_id", JsonArray.of(JsonObject.EMPTY.put("$eq", SharedSecretJwtAuthenticator.SECRET_DOCUMENT_ID))))
                .put("limit", 1));

        JsonObject result = mongoJsonDB.runCommand(findCommand);
        JsonArray firstBatch = result.get("cursor", JsonObject.EMPTY).get("firstBatch", JsonArray.EMPTY).getJsonArray();

        if (!firstBatch.isEmpty()) {
            System.out.println("[dcentb] jwt secret exists   (secret in database collection '" + collection + "' as property 'secret')");
            return firstBatch.get(0).getJsonObject().get("secret").getString();
        }

        String secret = generateSecret();
        JsonObject document = JsonObject.EMPTY.put("_id", SharedSecretJwtAuthenticator.SECRET_DOCUMENT_ID).put("secret", secret);
        mongoJsonDB.runCommand(JsonObject.EMPTY.put("insert", JsonObject.EMPTY
                .put("collection", collection)
                .put("documents", JsonArray.of(document))));
        System.out.println("[dcentb] jwt secret generated  collection=" + collection);
        return secret;
    }

    private static String generateSecret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
