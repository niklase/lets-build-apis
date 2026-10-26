package com.zuunr.dcentb.rest.processor.html;

import com.zuunr.dcentb.rest.processor.Processor;
import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;

/**
 * Builds the "/ui" dashboard body: {"collections": [{"name", "href", "displayName"}, ...]} -
 * see html-ui-rendering-design.md section 2. A collection is always listed, with
 * "displayName" falling back to the raw name at render time (ValueRenderer/
 * DisplayNameResolver) when "x-dcentb.collections.&lt;name&gt;.ui.displayName" isn't set.
 * <p>
 * Visibility is a coarse, best-effort check ("does any of the caller's permissions even
 * have an entry under this collection's own permissions block") - not the precise,
 * field-level check PreOperationAccessController/ResponseAccessController do on the real
 * GET. A collection slipping through here that the real GET would then 403 on is a
 * cosmetic dashboard inaccuracy, not a security hole - the actual enforcement is unchanged
 * and unaffected by this processor.
 * <p>
 * Sets UNRESTRICTED_ACCESS before producing the response: this body isn't a per-collection
 * API resource with field-level response schemas to apply (ResponseAccessController would
 * have nothing to filter against - see RequestHandlerBase, which always runs it next
 * regardless of which handler produced the response), and the only sensitive decision
 * here (which collections to even mention) was already made by visibleTo() above.
 */
public class CollectionsIndexBuilder extends Processor {

    private final JsonObject collectionsConfig;

    public CollectionsIndexBuilder(JsonValue config) {
        super(config);
        this.collectionsConfig = config.getJsonObject()
                .get(X_DCENTB, JsonObject.EMPTY).getJsonObject()
                .get("collections", JsonObject.EMPTY).getJsonObject();
    }

    @Override
    public JsonObject process(JsonObject requestContext) {
        if (requestContext.get(RESPONSE) != null) {
            return requestContext; // AuthenticationProcessor already produced a response (e.g. 401)
        }

        JsonValue authenticatedUser = requestContext.get("authenticatedUser");
        JsonArray callerPermissions = authenticatedUser == null ? null : authenticatedUser.get("permissions", JsonArray.EMPTY).getJsonArray();

        JsonArray collections = JsonArray.EMPTY;
        for (String name : collectionsConfig.keySet()) {
            JsonObject collectionConfig = collectionsConfig.get(name).getJsonObject();
            if (!visibleTo(callerPermissions, collectionConfig)) {
                continue;
            }
            JsonValue displayName = collectionConfig.get(JsonArray.of("ui", "displayName"));
            collections = collections.add(JsonObject.EMPTY
                    .put("name", name)
                    .put("href", "/" + name)
                    .put("displayName", displayName == null ? JsonObject.EMPTY.jsonValue() : displayName));
        }

        return requestContext
                .put(UNRESTRICTED_ACCESS, true)
                .put(RESPONSE, JsonObject.EMPTY.put("status", 200).put("body", JsonObject.EMPTY.put("collections", collections)));
    }

    private static boolean visibleTo(JsonArray callerPermissions, JsonObject collectionConfig) {
        if (callerPermissions == null) {
            return true; // no security scheme configured at all - the rest of the API is equally open
        }
        JsonObject permissions = collectionConfig.get("permissions", JsonObject.EMPTY).getJsonObject();
        for (JsonValue permission : callerPermissions) {
            String permissionName = permission.getString();
            if (SUPERUSER_PERMISSION.equals(permissionName) || permissions.containsKey(permissionName)) {
                return true;
            }
        }
        return false;
    }
}
