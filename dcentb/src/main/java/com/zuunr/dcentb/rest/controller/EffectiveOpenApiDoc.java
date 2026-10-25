package com.zuunr.dcentb.rest.controller;

import com.zuunr.dcentb.rest.processor.Processor;
import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The user-written OpenAPI document with every dcentb/mongodb default applied: each
 * operation's "x-dcentb.mongodb.collection" (the resolved "dbCollection" of whichever
 * logical collection its path belongs to), and a document-wide
 * "x-dcentb.mongodb.collectionSchemas" (one entry per distinct "dbCollection", so
 * {@link com.zuunr.mongodb.MongoJsonDB} can translate that collection's documents and
 * queries using its schema's "x-dbtype" instead of inferring BSON types from each JSON
 * value's own runtime type).
 * <p>
 * Every logical collection ("x-dcentb.collections.&lt;name&gt;", the same block
 * "permissions"/"stateTransitionSchema" already live in) that some path resolves to
 * must declare a "dbSchema" - either a real schema (a direct object, or
 * {@code {"$ref": "#/components/schemas/..."}}), or the deliberate opt-out sentinel
 * {@code true} / {@code {}} ("no typed schema for this collection, on purpose"). A
 * missing block, or a block with no "dbSchema" at all, fails fast at construction -
 * that's almost always a forgotten declaration, not a deliberate choice. An opted-out
 * collection still gets no entry under "collectionSchemas" (same default-mapping-plus-
 * warning fallback {@link com.zuunr.mongodb.MongoJsonDB} has always had), but still
 * gets its "dbCollection" resolved, since the physical Mongo collection name matters
 * regardless of whether it's typed.
 * <p>
 * "dbCollection" (the actual MongoDB collection name - may differ from the logical API
 * collection name) defaults to the collection's own key when not set explicitly, and is
 * always written back into the effective doc. It is no longer possible to set
 * "collection" directly on an operation's own "x-dcentb.mongodb" - that was a footgun
 * (nothing stopped different operations on the same resource from pointing at different
 * physical collections); the single per-logical-collection "dbCollection" is now the
 * only source, applied uniformly to every operation on that collection's paths. Two
 * logical collections resolving to the same "dbCollection" while both having a real
 * (non-opted-out) "dbSchema" fails fast, since there'd be no way to tell which schema
 * should govern that one physical collection.
 * <p>
 * Built via {@code configuredOpenApiDocument.as(EffectiveOpenApiDoc.class)}, mirroring
 * {@link com.zuunr.mongodb.DatabaseTypedSchema}'s "resolve once, from a JsonValue
 * constructor" idiom. Deliberately doesn't know about externally-supplied deployment
 * config (connection string, database name) - {@link RequestHandlerProvider} merges
 * that into the document before this class ever sees it. This class only derives
 * defaults that are intrinsic to the document's own structure.
 */
public final class EffectiveOpenApiDoc {

    private static final String SCHEMAS_REF_PREFIX = "#/components/schemas/";
    private static final String COLLECTIONS = "collections";
    private static final String DB_SCHEMA = "dbSchema";
    private static final String DB_COLLECTION = "dbCollection";

    private final JsonValue jsonValue;

    public EffectiveOpenApiDoc(JsonValue configuredOpenApiDocument) {
        JsonObject document = configuredOpenApiDocument.getJsonObject();
        JsonObject components = document.get("components", JsonObject.EMPTY).getJsonObject();
        JsonObject paths = document.get("paths").getJsonObject();
        JsonObject collectionsConfig = document.get(Processor.X_DCENTB, JsonObject.EMPTY).getJsonObject().get(COLLECTIONS, JsonObject.EMPTY).getJsonObject();

        Map<String, CollectionResolution> resolutionsByLogicalName = resolveCollections(paths, collectionsConfig, components);

        document = document
                .put(JsonArray.of(Processor.X_DCENTB, "mongodb", "collectionSchemas"), buildCollectionSchemas(resolutionsByLogicalName).jsonValue())
                .put(JsonArray.of(Processor.X_DCENTB, COLLECTIONS), withDbCollectionsWritten(collectionsConfig, resolutionsByLogicalName).jsonValue());

        JsonObject updatedPaths = applyOperationDefaults(paths, resolutionsByLogicalName);
        this.jsonValue = document.put("paths", updatedPaths.jsonValue()).jsonValue();
    }

    public JsonValue getJsonValue() {
        return jsonValue;
    }

    /** One logical collection's resolved physical name, and its typed schema - or null if it opted out. */
    private record CollectionResolution(String dbCollection, JsonValue resolvedSchema) {
    }

    // ---- resolving every logical collection any path actually belongs to ---------------

    private static Map<String, CollectionResolution> resolveCollections(JsonObject paths, JsonObject collectionsConfig, JsonObject components) {
        Map<String, CollectionResolution> resolutionsByLogicalName = new LinkedHashMap<>();
        for (String path : paths.keySet()) {
            String logicalName = collectionNameFromPath(path);
            resolutionsByLogicalName.computeIfAbsent(logicalName, name -> resolveCollection(name, collectionsConfig, components));
        }
        return resolutionsByLogicalName;
    }

    private static CollectionResolution resolveCollection(String logicalName, JsonObject collectionsConfig, JsonObject components) {
        JsonObject collectionConfig = collectionsConfig.get(logicalName, JsonValue.NULL).getJsonObject();
        if (collectionConfig == null) {
            throw new IllegalStateException("A path resolves to collection \"" + logicalName + "\", but no \"x-dcentb.collections."
                    + logicalName + "\" is declared. Add that block with a \"dbSchema\" - or \"dbSchema\": true if this collection "
                    + "is intentionally untyped.");
        }
        JsonValue dbSchemaValue = collectionConfig.get(DB_SCHEMA);
        if (dbSchemaValue == null) {
            throw new IllegalStateException("\"x-dcentb.collections." + logicalName + ".dbSchema\" is missing. Add a real schema, "
                    + "or \"true\"/\"{}\" to explicitly allow anything for this collection.");
        }
        String dbCollection = collectionConfig.get(DB_COLLECTION, JsonValue.of(logicalName)).getString();

        if (isOptedOut(dbSchemaValue)) {
            return new CollectionResolution(dbCollection, null);
        }

        JsonObject resolvedSchema = resolveRefIfPresent(dbSchemaValue, components);
        if (resolvedSchema == null) {
            throw new IllegalStateException("\"x-dcentb.collections." + logicalName + ".dbSchema\" could not be resolved - "
                    + "it is neither a schema object nor a resolvable $ref.");
        }
        // Attach "components" so DatabaseTypedSchema's own $ref resolution (e.g. a nested
        // "meta": {"$ref": "#/components/schemas/Meta_item_v1"}) can resolve against it -
        // it always resolves pointers relative to whatever root document it was given.
        JsonValue schemaWithId = withIdMirroringMetaId(resolvedSchema, components).put("components", components.jsonValue()).jsonValue();
        return new CollectionResolution(dbCollection, schemaWithId);
    }

    /** JSON Schema's own "matches anything" sentinels - the deliberate "no typed schema" opt-out. */
    private static boolean isOptedOut(JsonValue dbSchema) {
        return (dbSchema.isBoolean() && Boolean.TRUE.equals(dbSchema.getBoolean()))
                || (dbSchema.isJsonObject() && dbSchema.getJsonObject().isEmpty());
    }

    // ---- assembling collectionSchemas and the written-back dbCollection defaults -------

    private static JsonObject buildCollectionSchemas(Map<String, CollectionResolution> resolutionsByLogicalName) {
        JsonObject collectionSchemas = JsonObject.EMPTY;
        Map<String, String> logicalNameByDbCollection = new LinkedHashMap<>();
        for (Map.Entry<String, CollectionResolution> entry : resolutionsByLogicalName.entrySet()) {
            String logicalName = entry.getKey();
            CollectionResolution resolution = entry.getValue();
            if (resolution.resolvedSchema() == null) {
                continue; // opted out - no entry, same default-mapping fallback as an unregistered collection
            }
            String dbCollection = resolution.dbCollection();
            String conflictingLogicalName = logicalNameByDbCollection.put(dbCollection, logicalName);
            if (conflictingLogicalName != null) {
                throw new IllegalStateException("Collections \"" + conflictingLogicalName + "\" and \"" + logicalName
                        + "\" both resolve to dbCollection \"" + dbCollection + "\" while both have a real dbSchema - "
                        + "give them different \"dbCollection\" values.");
            }
            collectionSchemas = collectionSchemas.put(dbCollection, resolution.resolvedSchema());
        }
        return collectionSchemas;
    }

    private static JsonObject withDbCollectionsWritten(JsonObject collectionsConfig, Map<String, CollectionResolution> resolutionsByLogicalName) {
        JsonObject updated = collectionsConfig;
        for (Map.Entry<String, CollectionResolution> entry : resolutionsByLogicalName.entrySet()) {
            updated = updated.put(JsonArray.of(entry.getKey(), DB_COLLECTION), JsonValue.of(entry.getValue().dbCollection()));
        }
        return updated;
    }

    /**
     * MongoDB stores the API's "meta.id" as a top-level "_id" (see
     * NewStateToDatabaseItemCreator/MongoToApiItemTranslator) - a resource's own schema
     * only ever declares the nested "meta.id", so DatabaseTypedSchema would never see a
     * top-level "_id" leaf to type on its own. Synthesizing "_id" as a copy of "meta.id"'s
     * own resolved schema - rather than hardcoding x-dbtype "ObjectId" - keeps this correct
     * both for auto-generated ids and for caller-supplied ones (slugs, UUIDs via PUT):
     * whatever "meta.id" is declared as is what actually ends up stored at "_id". A
     * resource with no "meta.id" (or no "meta" at all) gets no synthesized "_id" - same
     * "leave it to the default mapping" fallback used everywhere else in this class.
     */
    private static JsonObject withIdMirroringMetaId(JsonObject schema, JsonObject components) {
        JsonValue idSchema = metaIdSchema(schema, components);
        return idSchema == null ? schema : schema.put(JsonArray.of("properties", "_id"), idSchema);
    }

    private static JsonValue metaIdSchema(JsonObject resourceSchema, JsonObject components) {
        JsonObject metaSchema = resolveRefIfPresent(resourceSchema.get("properties", JsonObject.EMPTY).getJsonObject().get("meta"), components);
        if (metaSchema == null) {
            return null;
        }
        JsonObject idSchema = resolveRefIfPresent(metaSchema.get("properties", JsonObject.EMPTY).getJsonObject().get("id"), components);
        return idSchema == null ? null : idSchema.jsonValue();
    }

    private static JsonObject resolveRefIfPresent(JsonValue schema, JsonObject components) {
        if (schema == null) {
            return null;
        }
        JsonObject schemaObject = schema.getJsonObject();
        if (schemaObject == null) {
            return null;
        }
        JsonValue ref = schemaObject.get("$ref");
        if (ref == null || !ref.isString() || !ref.getString().startsWith(SCHEMAS_REF_PREFIX)) {
            return schemaObject;
        }
        String refName = ref.getString().substring(SCHEMAS_REF_PREFIX.length());
        return components.get("schemas", JsonObject.EMPTY).getJsonObject().get(refName, JsonValue.NULL).getJsonObject();
    }

    // ---- per-operation defaulting: only "collection" now, never read as an input -------

    private static JsonObject applyOperationDefaults(JsonObject paths, Map<String, CollectionResolution> resolutionsByLogicalName) {
        JsonObject updatedPaths = paths;
        for (String path : paths.keySet()) {
            String dbCollection = resolutionsByLogicalName.get(collectionNameFromPath(path)).dbCollection();
            JsonObject operations = paths.get(path).getJsonObject();
            JsonObject updatedOperations = operations;
            for (String method : operations.keySet()) {
                JsonObject operation = operations.get(method).getJsonObject()
                        .put(JsonArray.of(Processor.X_DCENTB, "mongodb", "collection"), dbCollection);
                updatedOperations = updatedOperations.put(method, operation.jsonValue());
            }
            updatedPaths = updatedPaths.put(path, updatedOperations.jsonValue());
        }
        return updatedPaths;
    }

    private static String collectionNameFromPath(String path) {
        if (path.endsWith("/getCollection")) {
            path = path.substring(0, path.length() - "/getCollection".length());
        } else {
            int braceIndex = path.indexOf('{');
            if (braceIndex != -1) {
                path = path.substring(0, braceIndex);
            }
        }
        return path.replaceAll("^/+|/+$", "");
    }
}
