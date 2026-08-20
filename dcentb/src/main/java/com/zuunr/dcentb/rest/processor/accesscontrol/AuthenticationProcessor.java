package com.zuunr.dcentb.rest.processor.accesscontrol;

import com.zuunr.dcentb.rest.processor.Processor;
import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;

import java.util.ArrayList;
import java.util.List;

/**
 * Authenticates a request against the OAS "security" requirement in effect for the
 * operation, delegating the actual credential verification to one sub-processor per
 * security scheme referenced: ApiKeyAuthenticator for "apiKey", JwtAuthenticator for
 * "openIdConnect", SharedSecretJwtAuthenticator for a "http"/"bearer" scheme that isn't
 * an OIDC issuer (i.e. a self-issued JWT verified with a shared secret).
 *
 * Scheme selection follows standard OpenAPI semantics: an operation-level "security"
 * (even an empty array, meaning "no auth required") completely replaces the document's
 * root-level "security" default rather than merging with it. Each element of the
 * resulting "security" array is an alternative (tried in order, first fully-satisfied
 * one wins); scheme names within a single element must all succeed together (AND).
 *
 * Adding a new built-in scheme type is a matter of adding a case in buildAuthenticator();
 * letting dcentb users plug in their own scheme types/implementations is a separate,
 * not-yet-built extension point.
 */
public class AuthenticationProcessor extends Processor {

    private final List<List<Processor>> alternatives;

    public AuthenticationProcessor(JsonValue config) {
        super(config);

        JsonObject fullConfig = config.getJsonObject();
        JsonObject securitySchemes = fullConfig.get("components", JsonObject.EMPTY).getJsonObject()
                .get("securitySchemes", JsonObject.EMPTY).getJsonObject();

        JsonObject operation = fullConfig.get("operation", JsonObject.EMPTY).getJsonObject();
        JsonValue operationSecurity = operation.get("security");
        JsonArray security = (operationSecurity != null ? operationSecurity : fullConfig.get("security", JsonArray.EMPTY)).getJsonArray();

        this.alternatives = new ArrayList<>();
        for (JsonValue requirementValue : security) {
            JsonObject requirement = requirementValue.getJsonObject();
            List<Processor> andGroup = new ArrayList<>();
            for (String schemeName : requirement.keySet()) {
                JsonObject scheme = securitySchemes.get(schemeName, JsonObject.EMPTY).getJsonObject();
                andGroup.add(buildAuthenticator(schemeName, scheme, config));
            }
            if (!andGroup.isEmpty()) {
                alternatives.add(andGroup);
            }
        }
    }

    private static Processor buildAuthenticator(String schemeName, JsonObject scheme, JsonValue config) {
        String type = scheme.get("type", JsonValue.NULL).getString();
        if ("apiKey".equals(type)) {
            return config.as(ApiKeyAuthenticator.class);
        } else if ("openIdConnect".equals(type)) {
            return scheme.jsonValue().as(JwtAuthenticator.class);
        } else if ("http".equals(type) && "bearer".equalsIgnoreCase(scheme.get("scheme", JsonValue.NULL).getString())) {
            return config.as(SharedSecretJwtAuthenticator.class);
        }
        throw new IllegalStateException("Unsupported or missing 'type' for security scheme '" + schemeName + "': " + type);
    }

    @Override
    public JsonObject process(JsonObject requestContext) {
        if (alternatives.isEmpty()) {
            return requestContext;
        }

        JsonObject lastFailureResponse = null;
        for (List<Processor> andGroup : alternatives) {
            JsonObject authenticatedUser = JsonObject.EMPTY;
            boolean allSucceeded = true;

            for (Processor authenticator : andGroup) {
                JsonObject result = authenticator.process(requestContext);
                JsonValue response = result.get(RESPONSE);
                if (response != null) {
                    lastFailureResponse = response.getJsonObject();
                    allSucceeded = false;
                    break;
                }
                authenticatedUser = mergeInto(authenticatedUser, result.get("authenticatedUser", JsonObject.EMPTY).getJsonObject());
            }

            if (allSucceeded) {
                return requestContext.put("authenticatedUser", authenticatedUser);
            }
        }

        return requestContext.put(RESPONSE, lastFailureResponse != null
                ? lastFailureResponse
                : JsonObject.EMPTY.put("status", 401).put("message", "Authentication required"));
    }

    private static JsonObject mergeInto(JsonObject accumulator, JsonObject addition) {
        JsonObject merged = accumulator;
        for (String key : addition.keySet()) {
            merged = merged.put(key, addition.get(key));
        }
        return merged;
    }
}
