package com.zuunr.mongodb;

import java.util.Map;

/**
 * The part of a {@link DatabaseTypedSchema} needed to translate one JSON value: a
 * document-shaped node, an array-shaped node, a scalar leaf with a resolved
 * {@link BsonType}, or a node this translator has no type information for.
 * <p>
 * This mirrors the shape of the "properties"/"items" the schema actually declares -
 * it is the structure {@link Json2BsonTranslator} walks alongside the JSON data, not
 * the schema's own JSON representation.
 */
sealed interface SchemaNode {

    /** No "type" keyword and no "x-dbtype": translate using the JSON value's own runtime type. */
    SchemaNode UNTYPED = new Untyped();

    record Leaf(BsonType bsonType) implements SchemaNode {
    }

    record ObjectNode(Map<String, SchemaNode> properties) implements SchemaNode {

        SchemaNode property(String name) {
            SchemaNode node = properties.get(name);
            return node != null ? node : UNTYPED;
        }
    }

    record ArrayNode(SchemaNode items) implements SchemaNode {
    }

    record Untyped() implements SchemaNode {
    }

    /**
     * Resolves a (possibly dotted, e.g. "address.city") field path to the BSON type its
     * schema leaf resolved to - or, for a field that is itself an array, its items' leaf
     * type, so a scalar value can still be matched/set against an array field the way
     * Mongo does. Returns {@code null} - meaning "use the default mapping" - for an
     * untyped field, a field not in the schema, an object/array-of-objects field, a path
     * segment that doesn't resolve (e.g. an array index or positional "$"), or when
     * {@code rootNode} is null (no schema registered for this collection).
     */
    static BsonType resolveLeafType(ObjectNode rootNode, String fieldPath) {
        if (rootNode == null) {
            return null;
        }
        SchemaNode node = rootNode;
        for (String segment : fieldPath.split("\\.")) {
            if (!(node instanceof ObjectNode objectNode)) {
                return null;
            }
            node = objectNode.property(segment);
        }
        if (node instanceof Leaf leaf) {
            return leaf.bsonType();
        }
        if (node instanceof ArrayNode arrayNode && arrayNode.items() instanceof Leaf itemLeaf) {
            return itemLeaf.bsonType();
        }
        return null;
    }
}
