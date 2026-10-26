package com.zuunr.dcentb.rest.processor.accesscontrol;

import com.zuunr.dcentb.rest.html.ContentNegotiation;
import com.zuunr.dcentb.rest.html.CookieHeaderParser;
import com.zuunr.dcentb.rest.html.SessionCookie;
import com.zuunr.dcentb.rest.processor.Processor;
import com.zuunr.dcentb.rest.processor.mongo.MongoJsonDBHandle;
import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import com.zuunr.mongodb.MongoJsonDB;

public class ApiKeyAuthenticator extends Processor {

    static final String DEFAULT_COLLECTION = "api-keys";

    /**
     * Carries the same credential as the "api-key" header, but for a browser's own,
     * non-header-capable navigation (see html-ui-rendering-design.md section 8). Only ever
     * consulted when the "api-key" header is absent AND the request negotiated HTML - the
     * same ContentNegotiation.wantsHtml predicate the HTML renderer itself uses, so the two
     * can never disagree about whether a given request "wants HTML". JSON API behavior is
     * completely unchanged: no cookie is ever consulted for a request that didn't ask for HTML.
     */
    private static final String HTML_SESSION_COOKIE = SessionCookie.NAME;

    private final MongoJsonDB mongoJsonDB;
    private final String collection;

    public ApiKeyAuthenticator(JsonValue config) {
        super(config);
        this.mongoJsonDB = config.as(MongoJsonDBHandle.class).getMongoJsonDB();
        this.collection = config.getJsonObject()
                .get(X_DCENTB, JsonObject.EMPTY)
                .get("accessControl", JsonObject.EMPTY)
                .get("apiKeyCollection", DEFAULT_COLLECTION)
                .getString();
    }

    @Override
    public JsonObject process(JsonObject requestContext) {
        JsonObject request = requestContext.get(REQUEST).getJsonObject();
        JsonObject requestHeaders = request.get("headers", JsonObject.EMPTY).getJsonObject();
        String apiKey = requestHeaders.get("api-key", JsonArray.of("")).get(0).getString();

        if (apiKey.isEmpty() && ContentNegotiation.wantsHtml(requestHeaders)) {
            String cookieValue = CookieHeaderParser.parse(requestHeaders).get(HTML_SESSION_COOKIE, JsonValue.NULL).getString();
            apiKey = cookieValue == null ? "" : cookieValue;
        }

        if (apiKey.isEmpty()) {
            return requestContext.put(RESPONSE, JsonObject.EMPTY.put("status", 401).put("message", "Missing api-key"));
        }

        JsonObject findCommand = JsonObject.EMPTY.put("find", JsonObject.EMPTY
                .put("collection", collection)
                .put("filter", JsonObject.EMPTY
                        .put("_id", JsonArray.of(JsonObject.EMPTY.put("$eq", apiKey))))
                .put("limit", 1));

        JsonObject result = mongoJsonDB.runCommand(findCommand);
        JsonArray firstBatch = result.get("cursor", JsonObject.EMPTY).get("firstBatch", JsonArray.EMPTY).getJsonArray();

        if (firstBatch.isEmpty()) {
            return requestContext.put(RESPONSE, JsonObject.EMPTY.put("status", 401).put("message", "Invalid api-key"));
        }

        JsonObject authenticatedUser = firstBatch.get(0).getJsonObject().remove("_id");

        return requestContext.put("authenticatedUser", authenticatedUser);
    }
}
