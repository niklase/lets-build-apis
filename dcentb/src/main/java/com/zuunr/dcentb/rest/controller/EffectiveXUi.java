package com.zuunr.dcentb.rest.controller;

import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Completes "x-ui.properties" (see html-ui-rendering-design.md section 3) on every
 * object-typed schema reachable from a 2xx response body, the same "resolve every default
 * explicitly, once, at startup" idiom as {@link EffectiveOpenApiDoc}: an author's own
 * x-ui.properties entries are left exactly as declared, then every remaining property of
 * that same schema is appended, sorted alphabetically by key, with no displayName (the
 * render-time renderer falls back to the raw key itself when displayName is absent).
 * <p>
 * Scope is deliberately narrow - only schemas reachable by walking "paths.*.*.responses.
 * <2xx>.content['application/json'].schema" (following "$ref" and recursing into nested
 * object properties and array "items"). Request bodies, non-2xx responses, and any
 * "components.schemas" entry nothing references are left completely untouched.
 * <p>
 * A schema reached via "$ref" is completed exactly once and the completed version is
 * written back into "components.schemas" under its own name - every usage site keeps
 * pointing at the same "$ref", so (deliberately, see the design doc's risk list) a shared
 * schema's labels/order are identical everywhere it's used. A schema reached inline
 * (never "$ref"'d) is completed in place, at that one occurrence, since nothing else could
 * share it. Self-referential/cyclical schemas are guarded against infinite recursion: a
 * ref already "in progress" on the current path is left as-is and not re-entered - whatever
 * frame started it finishes the job once.
 */
public final class EffectiveXUi {

    private static final String SCHEMAS_REF_PREFIX = "#/components/schemas/";
    private static final String X_UI = "x-ui";
    private static final String PROPERTIES = "properties";

    private final JsonValue jsonValue;

    public EffectiveXUi(JsonValue configuredOpenApiDocument) {
        JsonObject document = configuredOpenApiDocument.getJsonObject();
        JsonObject originalComponentSchemas = document.get("components", JsonObject.EMPTY).getJsonObject()
                .get("schemas", JsonObject.EMPTY).getJsonObject();
        JsonObject paths = document.get("paths", JsonObject.EMPTY).getJsonObject();

        SchemaWalker walker = new SchemaWalker(originalComponentSchemas);

        JsonObject updatedPaths = paths;
        for (String path : paths.keySet()) {
            JsonObject operations = paths.get(path).getJsonObject();
            JsonObject updatedOperations = operations;
            for (String method : operations.keySet()) {
                JsonObject operation = operations.get(method).getJsonObject();
                JsonObject responses = operation.get("responses", JsonObject.EMPTY).getJsonObject();
                JsonObject updatedResponses = responses;
                for (String statusCode : responses.keySet()) {
                    if (!is2xx(statusCode)) {
                        continue;
                    }
                    JsonArray schemaPath = JsonArray.of(statusCode, "content", "application/json", "schema");
                    JsonValue schema = responses.get(schemaPath);
                    if (schema == null) {
                        continue;
                    }
                    JsonValue completedSchema = walker.complete(schema, new HashSet<>());
                    updatedResponses = updatedResponses.put(schemaPath, completedSchema);
                }
                updatedOperations = updatedOperations.put(JsonArray.of(method, "responses"), updatedResponses.jsonValue());
            }
            updatedPaths = updatedPaths.put(path, updatedOperations.jsonValue());
        }

        document = document.put(JsonArray.of("components", "schemas"), walker.updatedComponentSchemas().jsonValue());
        this.jsonValue = document.put("paths", updatedPaths.jsonValue()).jsonValue();
    }

    public JsonValue getJsonValue() {
        return jsonValue;
    }

    private static boolean is2xx(String statusCode) {
        return statusCode.matches("2\\d\\d");
    }

    /**
     * Carries the one mutable piece of state across the whole walk: the completed version
     * of each distinct "components.schemas" entry touched so far, keyed by its ref name -
     * written once, reused by every later "$ref" to that same name.
     */
    private static final class SchemaWalker {

        private final Map<String, JsonValue> componentSchemas; // mutable - updated in place as refs are completed

        SchemaWalker(JsonObject originalComponentSchemas) {
            this.componentSchemas = new LinkedHashMap<>();
            for (String name : originalComponentSchemas.keySet()) {
                componentSchemas.put(name, originalComponentSchemas.get(name));
            }
        }

        JsonObject updatedComponentSchemas() {
            JsonObject result = JsonObject.EMPTY;
            for (Map.Entry<String, JsonValue> entry : componentSchemas.entrySet()) {
                result = result.put(entry.getKey(), entry.getValue());
            }
            return result;
        }

        /**
         * @param inProgressRefNames ref names currently being completed higher up this same
         *                           recursion path - a cycle guard, not shared across sibling
         *                           branches (a diamond-shaped, non-cyclical reference to the
         *                           same schema from two different properties is fine and is
         *                           exactly the "completed once, reused" case this is built for).
         */
        JsonValue complete(JsonValue schemaNode, Set<String> inProgressRefNames) {
            if (schemaNode == null) {
                return null;
            }
            JsonObject schemaObject = schemaNode.getJsonObject();
            if (schemaObject == null) {
                return schemaNode;
            }

            String refName = refName(schemaObject);
            if (refName != null) {
                if (!inProgressRefNames.contains(refName)) {
                    Set<String> nested = new HashSet<>(inProgressRefNames);
                    nested.add(refName);
                    JsonValue target = componentSchemas.get(refName);
                    if (target != null) {
                        componentSchemas.put(refName, completeObjectOrArray(target, nested));
                    }
                }
                return schemaNode; // callers keep pointing at the $ref; the referenced schema was updated in place above
            }

            return completeObjectOrArray(schemaNode, inProgressRefNames);
        }

        private JsonValue completeObjectOrArray(JsonValue schemaNode, Set<String> inProgressRefNames) {
            JsonObject schema = schemaNode.getJsonObject();
            if (schema == null) {
                return schemaNode;
            }

            JsonValue items = schema.get("items");
            if (items != null) {
                return schema.put("items", complete(items, inProgressRefNames)).jsonValue();
            }

            JsonObject properties = schema.get(PROPERTIES, JsonValue.NULL).getJsonObject();
            if (properties == null) {
                return schemaNode; // leaf: scalar, enum, free-form object with no declared properties, etc.
            }

            JsonObject updatedProperties = properties;
            for (String propertyName : properties.keySet()) {
                updatedProperties = updatedProperties.put(propertyName, complete(properties.get(propertyName), inProgressRefNames));
            }

            JsonArray completedXUiProperties = completeXUiProperties(schema, properties);

            return schema
                    .put(PROPERTIES, updatedProperties.jsonValue())
                    .put(JsonArray.of(X_UI, PROPERTIES), completedXUiProperties.jsonValue())
                    .jsonValue();
        }

        private JsonArray completeXUiProperties(JsonObject schema, JsonObject properties) {
            JsonArray declared = schema.get(JsonArray.of(X_UI, PROPERTIES), JsonArray.EMPTY).getJsonArray();

            Set<String> declaredNames = new LinkedHashSet<>();
            JsonArray completed = JsonArray.EMPTY;
            for (JsonValue entry : declared) {
                String name = entry.get("name").getString();
                if (!properties.containsKey(name)) {
                    throw new IllegalStateException("x-ui.properties lists \"" + name
                            + "\", but this schema has no such property. Fix the name, or remove the entry.");
                }
                declaredNames.add(name);
                completed = completed.add(entry);
            }

            List<String> remainder = new ArrayList<>();
            for (String propertyName : properties.keySet()) {
                if (!declaredNames.contains(propertyName)) {
                    remainder.add(propertyName);
                }
            }
            remainder.sort(String::compareTo);
            for (String propertyName : remainder) {
                completed = completed.add(JsonObject.EMPTY.put("name", propertyName));
            }

            return completed;
        }

        private static String refName(JsonObject schemaObject) {
            JsonValue ref = schemaObject.get("$ref");
            if (ref == null || !ref.isString() || !ref.getString().startsWith(SCHEMAS_REF_PREFIX)) {
                return null;
            }
            return ref.getString().substring(SCHEMAS_REF_PREFIX.length());
        }
    }
}
