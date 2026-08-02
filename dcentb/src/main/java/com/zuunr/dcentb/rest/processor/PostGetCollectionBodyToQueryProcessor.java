package com.zuunr.dcentb.rest.processor;

import com.zuunr.json.*;

/**
 * Converts a POST /…/getCollection request body into the flat query-parameter
 * format that MongoJsonDBCommandCreator expects, mirroring what a GET request
 * with equivalent URL query parameters would produce.
 *
 * Must be placed before OASRequestDeserializer so the body is removed before
 * OAS validation runs (the getCollection operation has no requestBody spec).
 *
 * Body format:
 * <pre>
 * {
 *   "filter": { "field.path": { "operator": value } },
 *   "limit":  10,
 *   "offset": 0
 * }
 * </pre>
 * Each value is wrapped in a single-element JsonArray to match the
 * multi-value query-parameter representation used throughout the pipeline.
 */
public class PostGetCollectionBodyToQueryProcessor extends Processor {

    private static final String GET_COLLECTION_SUFFIX = "/getCollection";

    @Override
    public JsonObject process(JsonObject requestContext) {

        JsonObject request = requestContext.get(REQUEST).getJsonObject();

        JsonValue methodValue = request.get("method");
        if (methodValue == null || !"POST".equals(methodValue.getString())) {
            return requestContext;
        }

        JsonValue uriValue = request.get("uri");
        if (uriValue == null) {
            return requestContext;
        }
        String uri = uriValue.getString();
        int queryStart = uri.indexOf('?');
        String path = queryStart == -1 ? uri : uri.substring(0, queryStart);
        if (!path.endsWith(GET_COLLECTION_SUFFIX)) {
            return requestContext;
        }

        JsonObject body = request.get("body").getJsonObject();


        JsonObjectBuilder queryBuilder = JsonObject.EMPTY.builder();

        JsonValue limit = body.get("limit");
        if (limit != null) {
            queryBuilder.put("limit", JsonArray.of(limit));
        }

        JsonValue offset = body.get("offset");
        if (offset != null) {
            queryBuilder.put("offset", JsonArray.of(offset));
        }

        JsonObject filter = body.get("filter", JsonValue.NULL).getJsonObject();
        if (filter != null) {
            JsonArray fieldNames = filter.keys();
            JsonArray fieldValues = filter.values();
            for (int i = 0; i < filter.size(); i++) {
                String fieldName = fieldNames.get(i).getString();
                JsonObject constraints = fieldValues.get(i, JsonValue.NULL).getJsonObject();
                if (constraints == null) {
                    continue;
                }
                JsonArray constraintKeys = constraints.keys();
                JsonArray constraintValues = constraints.values();
                for (int j = 0; j < constraints.size(); j++) {
                    String constraint = constraintKeys.get(j).getString();
                    JsonValue constraintValue = constraintValues.get(j);
                    queryBuilder.put("filter." + fieldName + "." + constraint, JsonArray.of(constraintValue));
                }
            }
        }

        JsonObject updatedRequest = request.remove("body").put("query", queryBuilder.build().jsonValue());
        return requestContext.put(REQUEST, updatedRequest);
    }
}