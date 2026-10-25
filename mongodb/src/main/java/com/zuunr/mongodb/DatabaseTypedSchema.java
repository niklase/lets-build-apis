package com.zuunr.mongodb;

import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import com.zuunr.json.pointer.JsonPointer;
import com.zuunr.json.schema.Keywords;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A JSON Schema, resolved once into the BSON type every leaf property/item should be
 * stored as. Built via {@code jsonSchema.as(DatabaseTypedSchema.class)}, which caches
 * the result on that {@link JsonValue} instance, so this constructor - and every
 * lookup and {@code $ref}/default resolution it does - runs exactly once per schema.
 * <p>
 * Scope, by design (see mongodb-types-from-schema.md):
 * <ul>
 *     <li>Only the root schema is used. A root {@code $ref} is followed, but only when
 *     the root has no "type"/"properties"/"items" of its own - the same rule applies
 *     one level down at every property/item, so a developer can still factor out a
 *     shared leaf schema (e.g. one "ObjectId" definition) via {@code $ref}.</li>
 *     <li>{@code allOf}/{@code oneOf}/{@code anyOf} are not merged or resolved.</li>
 *     <li>Only "properties" and "items" are investigated; "items" must be a single
 *     schema (tuple-style {@code items} arrays are rejected).</li>
 *     <li>Exactly one non-null "type" is supported per property/item. "x-dbtype" is a
 *     single string, not an array.</li>
 *     <li>Never reloaded: once built, a schema's resolved types are fixed for the life
 *     of the process.</li>
 * </ul>
 */
public final class DatabaseTypedSchema {

    private static final String X_DBTYPE = "x-dbtype";

    private final SchemaNode.ObjectNode rootNode;
    private final JsonValue jsonValueWithDefaults;

    public DatabaseTypedSchema(JsonValue schema) {
        List<LeafAnnotation> leafAnnotations = new ArrayList<>();
        SchemaNode root = buildNode(schema, schema, JsonArray.EMPTY, leafAnnotations);
        if (!(root instanceof SchemaNode.ObjectNode objectNode)) {
            throw new DatabaseTypedSchemaException("The root of a database typed schema must describe a JSON object (type: \"object\").");
        }
        this.rootNode = objectNode;
        this.jsonValueWithDefaults = annotate(schema, leafAnnotations);
    }

    /**
     * The schema node to translate the document's own fields against - used to look up
     * each top-level property's {@link SchemaNode} while walking a JSON document.
     */
    SchemaNode.ObjectNode rootNode() {
        return rootNode;
    }

    /**
     * The original schema, unchanged, except every leaf now carries the "x-dbtype" it
     * resolved to (explicit or defaulted) - lets a developer see how defaults were
     * applied without re-deriving the rules by hand.
     */
    public JsonValue getJsonValue() {
        return jsonValueWithDefaults;
    }

    private record LeafAnnotation(JsonArray path, BsonType bsonType) {
    }

    // ---- building the SchemaNode tree -------------------------------------------------

    private SchemaNode buildNode(JsonValue subschema, JsonValue rootDocument, JsonArray path, List<LeafAnnotation> leafAnnotations) {

        JsonValue resolved = resolveRef(subschema, rootDocument, path);
        JsonValue type = resolved.get(Keywords.TYPE);
        JsonValue xDbType = resolved.get(X_DBTYPE);

        if (containsType(type, Keywords.OBJECT)) {
            rejectXDbType(xDbType, path, "object");
            return buildObjectNode(resolved, rootDocument, path, leafAnnotations);
        }
        if (containsType(type, Keywords.ARRAY)) {
            rejectXDbType(xDbType, path, "array");
            return buildArrayNode(resolved, rootDocument, path, leafAnnotations);
        }
        if (type == null && xDbType == null) {
            return SchemaNode.UNTYPED;
        }

        String leafType = singleNonNullType(type, path);
        BsonType bsonType = resolveBsonType(leafType, resolved.get(Keywords.FORMAT), xDbType, path);
        leafAnnotations.add(new LeafAnnotation(path, bsonType));
        return new SchemaNode.Leaf(bsonType);
    }

    /**
     * A subschema's own "type"/"properties"/"items" always win over "$ref" on the same
     * node - at the root, and at every property/item one level down. Chained
     * {@code $ref}s are followed until a schema with its own keywords is found.
     */
    private JsonValue resolveRef(JsonValue subschema, JsonValue rootDocument, JsonArray path) {
        JsonValue ref = subschema.get(Keywords.REF);
        if (ref == null || hasOwnKeywords(subschema)) {
            return subschema;
        }
        JsonValue target = rootDocument.get(JsonPointer.of(ref.getString()).asArray());
        if (target == null) {
            throw new DatabaseTypedSchemaException("Unresolvable \"$ref\": \"" + ref.getString() + "\" at " + pathToString(path));
        }
        return resolveRef(target, rootDocument, path);
    }

    private boolean hasOwnKeywords(JsonValue subschema) {
        return subschema.get(Keywords.TYPE) != null
                || subschema.get(Keywords.PROPERTIES) != null
                || subschema.get(Keywords.ITEMS) != null;
    }

    private SchemaNode.ObjectNode buildObjectNode(JsonValue resolved, JsonValue rootDocument, JsonArray path, List<LeafAnnotation> leafAnnotations) {

        JsonValue propertiesValue = resolved.get(Keywords.PROPERTIES);
        Map<String, SchemaNode> properties = new HashMap<>();
        if (propertiesValue != null) {
            JsonObject propertiesObject = propertiesValue.getJsonObject();
            for (JsonValue key : propertiesObject.keys()) {
                String name = key.getString();
                JsonArray propertyPath = path.add(Keywords.PROPERTIES).add(name);
                properties.put(name, buildNode(propertiesObject.get(name), rootDocument, propertyPath, leafAnnotations));
            }
        }
        return new SchemaNode.ObjectNode(Map.copyOf(properties));
    }

    private SchemaNode.ArrayNode buildArrayNode(JsonValue resolved, JsonValue rootDocument, JsonArray path, List<LeafAnnotation> leafAnnotations) {

        JsonValue itemsValue = resolved.get(Keywords.ITEMS);
        if (itemsValue == null) {
            return new SchemaNode.ArrayNode(SchemaNode.UNTYPED);
        }
        if (itemsValue.isJsonArray()) {
            throw new DatabaseTypedSchemaException(
                    "Tuple-style \"items\" (an array of schemas) is not supported at " + pathToString(path) + "; use a single schema for \"items\".");
        }
        JsonArray itemsPath = path.add(Keywords.ITEMS);
        return new SchemaNode.ArrayNode(buildNode(itemsValue, rootDocument, itemsPath, leafAnnotations));
    }

    // ---- resolving one leaf's BsonType -------------------------------------------------

    private BsonType resolveBsonType(String jsonType, JsonValue formatValue, JsonValue xDbTypeValue, JsonArray path) {

        Set<BsonType> allowed = allowedBsonTypes(jsonType, path);
        if (xDbTypeValue == null) {
            String format = formatValue == null ? null : formatValue.getString();
            return defaultBsonType(jsonType, format, path);
        }

        BsonType explicit = BsonType.fromXDbType(requireSingleXDbTypeToken(xDbTypeValue, path));
        if (!allowed.contains(explicit)) {
            throw new DatabaseTypedSchemaException("x-dbtype \"" + explicit.xDbType() + "\" is not valid for type \""
                    + jsonType + "\" at " + pathToString(path) + ". Allowed for \"" + jsonType + "\": " + allowedTokens(allowed));
        }
        return explicit;
    }

    private static Set<BsonType> allowedBsonTypes(String jsonType, JsonArray path) {
        return switch (jsonType) {
            case Keywords.STRING -> Set.of(BsonType.STRING, BsonType.OBJECT_ID, BsonType.DATE);
            case Keywords.NUMBER -> Set.of(BsonType.DECIMAL128, BsonType.DOUBLE);
            case Keywords.INTEGER -> Set.of(BsonType.INT32, BsonType.INT64, BsonType.DECIMAL128);
            case Keywords.BOOLEAN -> Set.of(BsonType.BOOLEAN);
            default -> throw unsupportedType(jsonType, path);
        };
    }

    private static BsonType defaultBsonType(String jsonType, String format, JsonArray path) {
        return switch (jsonType) {
            case Keywords.STRING -> ("date".equals(format) || "date-time".equals(format)) ? BsonType.DATE : BsonType.STRING;
            case Keywords.NUMBER -> ("float".equals(format) || "double".equals(format)) ? BsonType.DOUBLE : BsonType.DECIMAL128;
            case Keywords.INTEGER -> "int32".equals(format) ? BsonType.INT32 : BsonType.INT64;
            case Keywords.BOOLEAN -> BsonType.BOOLEAN;
            default -> throw unsupportedType(jsonType, path);
        };
    }

    private static DatabaseTypedSchemaException unsupportedType(String jsonType, JsonArray path) {
        return new DatabaseTypedSchemaException("Unsupported JSON Schema \"type\": \"" + jsonType + "\" at " + pathToString(path));
    }

    private static String requireSingleXDbTypeToken(JsonValue xDbTypeValue, JsonArray path) {
        if (!xDbTypeValue.isString()) {
            throw new DatabaseTypedSchemaException("x-dbtype must be a single string at " + pathToString(path) + ", found: " + xDbTypeValue.asJson());
        }
        return xDbTypeValue.getString();
    }

    private static String allowedTokens(Set<BsonType> allowed) {
        return allowed.stream().map(BsonType::xDbType).sorted().reduce((a, b) -> a + ", " + b).orElse("");
    }

    private static void rejectXDbType(JsonValue xDbTypeValue, JsonArray path, String kind) {
        if (xDbTypeValue != null) {
            throw new DatabaseTypedSchemaException("x-dbtype is not supported on a \"" + kind + "\" schema, at " + pathToString(path));
        }
    }

    // ---- "type" handling ---------------------------------------------------------------

    private static boolean containsType(JsonValue type, String candidate) {
        if (type == null) {
            return false;
        }
        return type.isString() ? candidate.equals(type.getString()) : type.isJsonArray() && type.getJsonArray().contains(candidate);
    }

    /**
     * @return the sole non-null entry of "type" - as a bare string or as an array that
     * also contains "null" (e.g. {@code ["string","null"]}). Rejects a "type" array
     * naming more than one non-null type: multiple x-dbtype support was dropped, so
     * there is no single BSON type such a property could resolve to.
     */
    private static String singleNonNullType(JsonValue type, JsonArray path) {
        if (type == null) {
            throw new DatabaseTypedSchemaException("x-dbtype requires an explicit \"type\" at " + pathToString(path));
        }
        if (type.isString()) {
            return type.getString();
        }
        if (!type.isJsonArray()) {
            throw new DatabaseTypedSchemaException("\"type\" must be a string or an array of strings at " + pathToString(path));
        }
        List<String> nonNullTypes = new ArrayList<>();
        for (JsonValue entry : type.getJsonArray()) {
            if (!Keywords.NULL.equals(entry.getString())) {
                nonNullTypes.add(entry.getString());
            }
        }
        if (nonNullTypes.size() != 1) {
            throw new DatabaseTypedSchemaException("\"type\" must contain exactly one non-null type (plus optionally \"null\") at "
                    + pathToString(path) + ", found: " + type.asJson());
        }
        return nonNullTypes.get(0);
    }

    // ---- the debug-view JsonValue -------------------------------------------------------

    private static JsonValue annotate(JsonValue schema, List<LeafAnnotation> leafAnnotations) {
        JsonValue annotated = schema;
        for (LeafAnnotation annotation : leafAnnotations) {
            annotated = annotated.put(annotation.path().add(X_DBTYPE), JsonValue.of(annotation.bsonType().xDbType()));
        }
        return annotated;
    }

    private static String pathToString(JsonArray path) {
        return path.isEmpty() ? "<root>" : JsonPointer.of(path).toString();
    }
}
