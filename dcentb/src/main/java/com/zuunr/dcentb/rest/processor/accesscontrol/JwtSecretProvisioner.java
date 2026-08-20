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
 * tokens with, then - purely for demo purposes - signs and prints one JWT per entry of
 * "x-dcentb.jwtGeneration" to standard out, so a demo can exercise self-issued-JWT auth
 * without a real OIDC/Auth0/Firebase provider. Mirrors ApiKeyProvisioner.
 */
public class JwtSecretProvisioner {

    public void provision(JsonObject openApiDocument) {
        JsonValue jwtGenerationValue = openApiDocument.get(JsonArray.of("x-dcentb", "jwtGeneration"));
        if (jwtGenerationValue == null) {
            return;
        }
        JsonArray entries = jwtGenerationValue.getJsonArray();
        if (entries == null || entries.isEmpty()) {
            return;
        }

        JsonObject xDcentb = openApiDocument.get("x-dcentb", JsonObject.EMPTY).getJsonObject();
        JsonObject mongoConfig = xDcentb.get("mongodb", JsonObject.EMPTY).getJsonObject();
        MongoJsonDB mongoJsonDB = mongoConfig.as(MongoJsonDB.class);
        String collection = xDcentb.get("accessControl", JsonObject.EMPTY)
                .get("jwtSecretCollection", SharedSecretJwtAuthenticator.DEFAULT_COLLECTION)
                .getString();

        String secret = readOrCreateSecret(mongoJsonDB, collection);
        Algorithm algorithm = Algorithm.HMAC256(secret);

        for (int i = 0; i < entries.size(); i++) {
            JsonObject claims = entries.get(i).getJsonObject();
            String token = JWT.create().withPayload(claims.asJson()).sign(algorithm);
            System.out.println("[dcentb] jwt generated       claims=" + claims + "  token=" + token);
        }
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
