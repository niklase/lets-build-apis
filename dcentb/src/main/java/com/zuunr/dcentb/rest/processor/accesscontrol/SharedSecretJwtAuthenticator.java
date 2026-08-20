package com.zuunr.dcentb.rest.processor.accesscontrol;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.zuunr.dcentb.rest.processor.Processor;
import com.zuunr.dcentb.rest.processor.mongo.MongoJsonDBHandle;
import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import com.zuunr.json.JsonValueFactory;
import com.zuunr.mongodb.MongoJsonDB;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Authenticates requests by verifying a Bearer JWT (HMAC256) signed with a secret
 * shared only between dcentb and whatever issued the token (typically dcentb itself,
 * see JwtSecretProvisioner) - the sub-processor AuthenticationProcessor delegates to
 * for a "http"/"bearer" securityScheme that isn't an OIDC issuer.
 *
 * The secret itself is looked up per request in the same MongoDB collection
 * JwtSecretProvisioner reads/creates it in (default collection "jwt-secrets"),
 * rather than cached at construction time, so it can be rotated without an app
 * restart - mirroring how ApiKeyAuthenticator looks up api-keys per request.
 *
 * Configure as a security scheme:
 *   components:
 *     securitySchemes:
 *       SelfIssuedJwt:
 *         type: http
 *         scheme: bearer
 *         bearerFormat: JWT
 */
public class SharedSecretJwtAuthenticator extends Processor {

    static final String SECRET_DOCUMENT_ID = "shared-secret";
    static final String DEFAULT_COLLECTION = "jwt-secrets";

    private final MongoJsonDB mongoJsonDB;
    private final String collection;

    public SharedSecretJwtAuthenticator(JsonValue config) {
        super(config);
        this.mongoJsonDB = config.as(MongoJsonDBHandle.class).getMongoJsonDB();
        this.collection = config.getJsonObject()
                .get(X_DCENTB, JsonObject.EMPTY)
                .get("accessControl", JsonObject.EMPTY)
                .get("jwtSecretCollection", DEFAULT_COLLECTION)
                .getString();
    }

    @Override
    public JsonObject process(JsonObject requestContext) {
        JsonObject request = requestContext.get(REQUEST).getJsonObject();
        String token = BearerTokenExtractor.extract(request);

        if (token == null || token.isEmpty()) {
            return requestContext.put(RESPONSE, JsonObject.EMPTY.put("status", 401).put("message", "Missing bearer token"));
        }

        String secret = readSecret();
        if (secret == null) {
            return requestContext.put(RESPONSE, JsonObject.EMPTY.put("status", 401).put("message", "No JWT secret provisioned"));
        }

        try {
            DecodedJWT verified = JWT.require(Algorithm.HMAC256(secret)).build().verify(token);

            String payloadJson = new String(Base64.getUrlDecoder().decode(verified.getPayload()), StandardCharsets.UTF_8);
            JsonObject authenticatedUser = JsonValueFactory.create(payloadJson).getJsonObject();

            return requestContext.put("authenticatedUser", authenticatedUser);
        } catch (JWTVerificationException e) {
            return requestContext.put(RESPONSE, JsonObject.EMPTY.put("status", 401).put("message", "Invalid bearer token"));
        }
    }

    private String readSecret() {
        JsonObject findCommand = JsonObject.EMPTY.put("find", JsonObject.EMPTY
                .put("collection", collection)
                .put("filter", JsonObject.EMPTY
                        .put("_id", JsonArray.of(JsonObject.EMPTY.put("$eq", SECRET_DOCUMENT_ID))))
                .put("limit", 1));

        JsonObject result = mongoJsonDB.runCommand(findCommand);
        JsonArray firstBatch = result.get("cursor", JsonObject.EMPTY).get("firstBatch", JsonArray.EMPTY).getJsonArray();

        return firstBatch.isEmpty() ? null : firstBatch.get(0).getJsonObject().get("secret", JsonValue.NULL).getString();
    }
}
