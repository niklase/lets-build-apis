package com.zuunr.dcentb.rest.processor.accesscontrol;

import com.zuunr.dcentb.rest.processor.Processor;
import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;

import java.util.ArrayList;
import java.util.List;

/**
 * Authenticates a request by trying, in order, the security schemes that apply to the
 * operation - delegating the actual credential verification to one sub-processor per
 * scheme type: ApiKeyAuthenticator for "apiKey", JwtAuthenticator for "openIdConnect",
 * SharedSecretJwtAuthenticator for a "http"/"bearer" scheme that isn't an OIDC issuer
 * (i.e. a self-issued JWT verified with a shared secret). The first scheme that
 * verifies the request wins.
 *
 * Only "components.securitySchemes" (standard OAS) is configured; there is no root or
 * per-operation "security" field, since dcentb never needs more than one scheme to
 * succeed (no AND-groups) and OAS's Security Requirement Object array exists mainly to
 * express those. Instead, an operation may list which of the declared schemes apply via
 * "x-dcentb.accessControl.securitySchemes" (an array of scheme-name strings, each a key
 * into "components.securitySchemes"); if omitted, every declared scheme is tried. Any
 * extra parameter a scheme's Authenticator needs beyond the standard OAS fields (e.g.
 * "audience", "apiKeyCollection") lives under that scheme's own "x-dcentb" object.
 *
 * Adding a new built-in scheme type is a matter of adding a case in buildAuthenticator();
 * letting dcentb users plug in their own scheme types/implementations is a separate,
 * not-yet-built extension point.
 */
public class AuthenticationProcessor extends Processor {

    private final List<Processor> alternatives;

    public AuthenticationProcessor(JsonValue config) {
        super(config);

        JsonObject fullConfig = config.getJsonObject();
        JsonObject securitySchemes = fullConfig.get("components", JsonObject.EMPTY).getJsonObject()
                .get("securitySchemes", JsonObject.EMPTY).getJsonObject();

        JsonObject operation = fullConfig.get("operation", JsonObject.EMPTY).getJsonObject();
        JsonValue schemeNamesOverride = operation.get(X_DCENTB, JsonObject.EMPTY).getJsonObject()
                .get("accessControl", JsonObject.EMPTY).getJsonObject()
                .get("securitySchemes");

        Iterable<String> schemeNames = schemeNamesOverride != null
                ? asStrings(schemeNamesOverride.getJsonArray())
                : securitySchemes.keySet();

        this.alternatives = new ArrayList<>();
        for (String schemeName : schemeNames) {
            JsonObject scheme = securitySchemes.get(schemeName, JsonObject.EMPTY).getJsonObject();
            alternatives.add(buildAuthenticator(schemeName, scheme, config));
        }
    }

    private static List<String> asStrings(JsonArray array) {
        List<String> result = new ArrayList<>();
        for (JsonValue value : array) {
            result.add(value.getString());
        }
        return result;
    }

    private static Processor buildAuthenticator(String schemeName, JsonObject scheme, JsonValue config) {
        String type = scheme.get("type", JsonValue.NULL).getString();
        JsonObject schemeExtras = scheme.get(X_DCENTB, JsonObject.EMPTY).getJsonObject();

        if ("apiKey".equals(type)) {
            JsonValue apiKeyCollection = schemeExtras.get("apiKeyCollection", ApiKeyAuthenticator.DEFAULT_COLLECTION);
            return config.getJsonObject()
                    .put(JsonArray.of(X_DCENTB, "accessControl", "apiKeyCollection"), apiKeyCollection)
                    .jsonValue()
                    .as(ApiKeyAuthenticator.class);
        } else if ("openIdConnect".equals(type)) {
            return scheme.jsonValue().as(JwtAuthenticator.class);
        } else if ("http".equals(type) && "bearer".equalsIgnoreCase(scheme.get("scheme", JsonValue.NULL).getString())) {
            JsonValue jwtSecretCollection = schemeExtras.get("jwtSecretCollection", SharedSecretJwtAuthenticator.DEFAULT_COLLECTION);
            return config.getJsonObject()
                    .put(JsonArray.of(X_DCENTB, "accessControl", "jwtSecretCollection"), jwtSecretCollection)
                    .jsonValue()
                    .as(SharedSecretJwtAuthenticator.class);
        }
        throw new IllegalStateException("Unsupported or missing 'type' for security scheme '" + schemeName + "': " + type);
    }

    @Override
    public JsonObject process(JsonObject requestContext) {
        if (alternatives.isEmpty()) {
            return requestContext;
        }

        JsonObject lastFailureResponse = null;
        for (Processor authenticator : alternatives) {
            JsonObject result = authenticator.process(requestContext);
            JsonValue response = result.get(RESPONSE);
            if (response == null) {
                return result;
            }
            lastFailureResponse = response.getJsonObject();
        }

        return requestContext.put(RESPONSE, lastFailureResponse);
    }
}
