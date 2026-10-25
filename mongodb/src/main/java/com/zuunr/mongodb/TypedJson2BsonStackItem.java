package com.zuunr.mongodb;

import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import org.bson.Document;

/**
 * Same walk as {@link Json2BsonStackItem}, carrying the matching {@link SchemaNode}
 * alongside the JSON value so each leaf can be converted using its resolved
 * {@link BsonType} instead of being inferred from the JSON value's own runtime type.
 * <p>
 * A node the schema has no type information for (not declared in "properties", or
 * never typed at all) falls back to {@link Json2BsonStackItem}'s existing behaviour -
 * same as a field translated without any schema today.
 */
final class TypedJson2BsonStackItem extends Json2BsonStackItem {

    private final SchemaNode schemaNode;

    TypedJson2BsonStackItem(JsonObject jsonObject, SchemaNode schemaNode) {
        super(jsonObject);
        this.schemaNode = schemaNode;
    }

    private TypedJson2BsonStackItem(JsonValue jsonValue, TypedJson2BsonStackItem parent, String parentKey, SchemaNode schemaNode) {
        super(jsonValue, parent, parentKey);
        this.schemaNode = schemaNode;
    }

    @Override
    public boolean translateObject() {
        if (schemaNode instanceof SchemaNode.ObjectNode) {
            // A typed object never gets the legacy single-field {"ObjectId": ".."} wrapper treatment below -
            // its own properties are typed individually, each via its own SchemaNode leaf.
            if (jsonValue.getJsonObject().isEmpty()) {
                setTranslated(new Document());
            }
            return getTranslated() != null;
        }
        return super.translateObject();
    }

    @Override
    public boolean translateLeaf() {
        if (schemaNode instanceof SchemaNode.Leaf leaf) {
            setTranslated(Json2BsonConverter.toBson(jsonValue, leaf.bsonType()));
            return true;
        }
        return super.translateLeaf();
    }

    @Override
    public JsonTranslatorStackItem<Object> createChild(JsonValue toBeTranslated, String parentKey) {
        return new TypedJson2BsonStackItem(toBeTranslated, this, parentKey, childSchemaNode(parentKey));
    }

    private SchemaNode childSchemaNode(String parentKey) {
        if (parentKey == null) { // array element
            return schemaNode instanceof SchemaNode.ArrayNode arrayNode ? arrayNode.items() : SchemaNode.UNTYPED;
        }
        return schemaNode instanceof SchemaNode.ObjectNode objectNode ? objectNode.property(parentKey) : SchemaNode.UNTYPED;
    }
}
