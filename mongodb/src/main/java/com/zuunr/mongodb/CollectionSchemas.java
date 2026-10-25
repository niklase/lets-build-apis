package com.zuunr.mongodb;

import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The "collectionSchemas" of a {@link MongoJsonDB} configuration:
 * <pre>
 * {
 *   "collectionSchemas": {
 *     "collectionName1": { ... extended JSON Schema with x-dbtype ... },
 *     "collectionName2": { ... }
 *   }
 * }
 * </pre>
 * Each schema is resolved into a {@link DatabaseTypedSchema} once, here, so a broken
 * schema fails fast when {@link MongoJsonDB} is built rather than on first use. A
 * collection with no registered schema still translates - using the same default,
 * runtime-type-based mapping as before this feature existed - but logs a warning the
 * first time that happens, since it usually means a schema was forgotten rather than
 * meant.
 */
final class CollectionSchemas {

    private static final Logger LOGGER = LoggerFactory.getLogger(CollectionSchemas.class);
    private static final String COLLECTION_SCHEMAS = "collectionSchemas";

    private final Map<String, DatabaseTypedSchema> schemasByCollection;
    private final Set<String> collectionsWarnedAbout = ConcurrentHashMap.newKeySet();

    CollectionSchemas(JsonObject config) {
        this.schemasByCollection = buildSchemas(config.get(COLLECTION_SCHEMAS, JsonValue.NULL).getJsonObject());
    }

    private static Map<String, DatabaseTypedSchema> buildSchemas(JsonObject collectionSchemas) {
        Map<String, DatabaseTypedSchema> schemas = new HashMap<>();
        if (collectionSchemas != null) {
            JsonArray collectionNames = collectionSchemas.keys();
            for (int i = 0; i < collectionNames.size(); i++) {
                String collection = collectionNames.get(i).getString();
                // Built directly, not via JsonValue.as(DatabaseTypedSchema.class): as() wraps whatever
                // the constructor throws in a reflection InvocationTargetException inside a
                // RuntimeException, which would hide DatabaseTypedSchemaException from a caller trying
                // to catch a broken schema at startup.
                schemas.put(collection, new DatabaseTypedSchema(collectionSchemas.get(collection)));
            }
        }
        return Map.copyOf(schemas);
    }

    /**
     * Translates one document against {@code collection}'s registered schema, falling
     * back to the default mapping (with a one-time warning) when none is registered. A
     * Mongo update modifier document (e.g. {@code {"$set": {...}}}) is handled field by
     * field instead - see {@link #translateModifierDocument}.
     */
    Document translateDocument(String collection, JsonObject document) {
        if (isModifierDocument(document)) {
            return translateModifierDocument(collection, document);
        }
        DatabaseTypedSchema schema = schemaFor(collection);
        return schema == null ? Json2BsonTranslator.translate(document) : Json2BsonTranslator.translate(document, schema);
    }

    /**
     * A modifier document's top-level keys are operators, not schema properties (e.g.
     * {@code {"$set": {"age": 42}, "$inc": {"views": 1}}}), so the schema can't be
     * applied to it directly the way {@link #translateDocument} applies it to a plain
     * document. For {@code $set}/{@code $inc} specifically, each key one level down
     * <i>is</i> a (possibly dotted, e.g. "address.city") schema field path, so those two
     * are resolved field by field against the collection's schema; every other operator
     * ({@code $unset}, {@code $push}, {@code $rename}, ...) keeps the default mapping,
     * since its operand generally isn't shaped like the field's own value at all.
     */
    private Document translateModifierDocument(String collection, JsonObject document) {
        SchemaNode.ObjectNode rootNode = rootNodeFor(collection);
        JsonArray operators = document.keys();
        Document translated = new Document();
        for (int i = 0; i < operators.size(); i++) {
            String operator = operators.get(i).getString();
            JsonObject operand = document.values().get(i).getJsonObject();
            translated.put(operator, isFieldValueOperator(operator)
                    ? translateFieldValues(rootNode, operand)
                    : Json2BsonTranslator.translate(operand));
        }
        return translated;
    }

    private static boolean isFieldValueOperator(String operator) {
        return "$set".equals(operator) || "$inc".equals(operator);
    }

    private static Document translateFieldValues(SchemaNode.ObjectNode rootNode, JsonObject fieldValues) {
        Document translated = new Document();
        JsonArray fieldPaths = fieldValues.keys();
        for (int i = 0; i < fieldPaths.size(); i++) {
            String fieldPath = fieldPaths.get(i).getString();
            JsonValue value = fieldValues.values().get(i);
            BsonType bsonType = SchemaNode.resolveLeafType(rootNode, fieldPath);
            translated.put(fieldPath, bsonType == null ? translateWithDefaultMapping(value) : coerceLeaf(value, bsonType));
        }
        return translated;
    }

    private static Object coerceLeaf(JsonValue value, BsonType bsonType) {
        return value.isNull() ? null : Json2BsonConverter.toBson(value, bsonType);
    }

    private static Object translateWithDefaultMapping(JsonValue value) {
        return Json2BsonTranslator.translate(JsonObject.EMPTY.put("value", value)).get("value");
    }

    /**
     * The schema node to resolve a query/filter field's BSON type against, or {@code null}
     * when {@code collection} has no registered schema (same one-time warning as
     * {@link #translateDocument}).
     */
    SchemaNode.ObjectNode rootNodeFor(String collection) {
        DatabaseTypedSchema schema = schemaFor(collection);
        return schema == null ? null : schema.rootNode();
    }

    private DatabaseTypedSchema schemaFor(String collection) {
        if (collection == null) {
            return null;
        }
        DatabaseTypedSchema schema = schemasByCollection.get(collection);
        if (schema == null) {
            warnOnce(collection);
        }
        return schema;
    }

    private static boolean isModifierDocument(JsonObject document) {
        JsonArray keys = document.keys();
        return !keys.isEmpty() && keys.get(0).getString().startsWith("$");
    }

    private void warnOnce(String collection) {
        if (collectionsWarnedAbout.add(collection)) {
            LOGGER.warn("No schema registered for collection \"{}\"; using the default JSON-to-BSON type mapping.", collection);
        }
    }
}
