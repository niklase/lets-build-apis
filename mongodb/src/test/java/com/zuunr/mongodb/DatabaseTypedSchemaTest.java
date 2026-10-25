package com.zuunr.mongodb;

import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonValue;
import com.zuunr.json.JsonValueFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DatabaseTypedSchemaTest {

    // ---- default mapping table (type/format -> x-dbtype when none is given explicitly) ----

    @ParameterizedTest(name = "type={0} format={1} -> x-dbtype {2}")
    @DisplayName("default x-dbtype per (type, format)")
    @CsvSource({
            "string,   ,           String",
            "string,   date,       Date",
            "string,   date-time,  Date",
            "string,   uuid,       String", // unrecognized format falls back to the type's own default
            "number,   ,           Decimal128",
            "number,   float,      Double",
            "number,   double,     Double",
            "integer,  ,           Int64",
            "integer,  int32,      Int32",
            "integer,  int64,      Int64",
            "boolean,  ,           Boolean",
    })
    void defaultXDbType(String type, String format, String expectedXDbType) {
        assertEquals(expectedXDbType, resolvedXDbType(fieldSchema(type, format, null)));
    }

    // ---- explicit x-dbtype: allowed overrides per type ----

    @ParameterizedTest(name = "type={0} x-dbtype={1}")
    @DisplayName("explicit x-dbtype accepted when compatible with the JSON Schema type")
    @CsvSource({
            "string,  String",
            "string,  ObjectId",
            "string,  Date",
            "number,  Decimal128",
            "number,  Double",
            "integer, Int32",
            "integer, Int64",
            "integer, Decimal128",
            "boolean, Boolean",
    })
    void explicitXDbTypeAccepted(String type, String xDbType) {
        assertEquals(xDbType, resolvedXDbType(fieldSchema(type, null, xDbType)));
    }

    @ParameterizedTest(name = "type={0} x-dbtype={1}")
    @DisplayName("explicit x-dbtype rejected when incompatible with the JSON Schema type")
    @CsvSource({
            "string,  Decimal128",
            "string,  Int32",
            "string,  Int64",
            "string,  Double",
            "string,  Boolean",
            "number,  ObjectId",
            "number,  Date",
            "number,  Int32",
            "number,  Boolean",
            "integer, ObjectId",
            "integer, Date",
            "integer, Double",
            "integer, String",
            "boolean, String",
            "boolean, Int32",
    })
    void incompatibleXDbTypeRejected(String type, String xDbType) {
        DatabaseTypedSchemaException exception = assertThrows(DatabaseTypedSchemaException.class,
                () -> new DatabaseTypedSchema(fieldSchema(type, null, xDbType)));
        assertContains(exception.getMessage(), "not valid for type");
    }

    @Test
    @DisplayName("unknown x-dbtype token is rejected")
    void unknownXDbTypeRejected() {
        DatabaseTypedSchemaException exception = assertThrows(DatabaseTypedSchemaException.class,
                () -> new DatabaseTypedSchema(fieldSchema("string", null, "ObjectID"))); // wrong casing, not a known token
        assertContains(exception.getMessage(), "Unknown x-dbtype");
    }

    @Test
    @DisplayName("x-dbtype as an array (rather than a single string) is rejected")
    void xDbTypeArrayRejected() {
        JsonValue schema = schema("""
                {"type":"object","properties":{"field":{"type":"string","x-dbtype":["String","Date"]}}}
                """);
        DatabaseTypedSchemaException exception = assertThrows(DatabaseTypedSchemaException.class, () -> new DatabaseTypedSchema(schema));
        assertContains(exception.getMessage(), "single string");
    }

    @Test
    @DisplayName("x-dbtype is rejected on an object schema")
    void xDbTypeOnObjectRejected() {
        JsonValue schema = schema("""
                {"type":"object","properties":{"nested":{"type":"object","x-dbtype":"String","properties":{}}}}
                """);
        DatabaseTypedSchemaException exception = assertThrows(DatabaseTypedSchemaException.class, () -> new DatabaseTypedSchema(schema));
        assertContains(exception.getMessage(), "\"object\" schema");
    }

    @Test
    @DisplayName("x-dbtype is rejected on an array schema")
    void xDbTypeOnArrayRejected() {
        JsonValue schema = schema("""
                {"type":"object","properties":{"tags":{"type":"array","x-dbtype":"String","items":{"type":"string"}}}}
                """);
        DatabaseTypedSchemaException exception = assertThrows(DatabaseTypedSchemaException.class, () -> new DatabaseTypedSchema(schema));
        assertContains(exception.getMessage(), "\"array\" schema");
    }

    @Test
    @DisplayName("tuple-style \"items\" (an array of schemas) is rejected")
    void tupleItemsRejected() {
        JsonValue schema = schema("""
                {"type":"object","properties":{"pair":{"type":"array","items":[{"type":"string"},{"type":"integer"}]}}}
                """);
        DatabaseTypedSchemaException exception = assertThrows(DatabaseTypedSchemaException.class, () -> new DatabaseTypedSchema(schema));
        assertContains(exception.getMessage(), "Tuple-style");
    }

    @Test
    @DisplayName("a \"type\" array naming more than one non-null type is rejected")
    void multipleNonNullTypesRejected() {
        JsonValue schema = schema("""
                {"type":"object","properties":{"field":{"type":["string","integer"]}}}
                """);
        DatabaseTypedSchemaException exception = assertThrows(DatabaseTypedSchemaException.class, () -> new DatabaseTypedSchema(schema));
        assertContains(exception.getMessage(), "exactly one non-null type");
    }

    @Test
    @DisplayName("\"null\" alongside exactly one other type is allowed")
    void nullAlongsideOneTypeAllowed() {
        JsonValue schema = schema("""
                {"type":"object","properties":{"field":{"type":["string","null"],"format":"date"}}}
                """);
        assertEquals("Date", resolvedXDbType(schema));
    }

    @Test
    @DisplayName("x-dbtype without an explicit \"type\" is rejected")
    void xDbTypeWithoutTypeRejected() {
        JsonValue schema = schema("""
                {"type":"object","properties":{"field":{"x-dbtype":"String"}}}
                """);
        DatabaseTypedSchemaException exception = assertThrows(DatabaseTypedSchemaException.class, () -> new DatabaseTypedSchema(schema));
        assertContains(exception.getMessage(), "requires an explicit \"type\"");
    }

    @Test
    @DisplayName("a property with no \"type\" and no \"x-dbtype\" is left untyped")
    void noTypeNoXDbTypeIsUntyped() {
        JsonValue schema = schema("""
                {"type":"object","properties":{"field":{}}}
                """);
        DatabaseTypedSchema typed = new DatabaseTypedSchema(schema);
        assertEquals(SchemaNode.UNTYPED, typed.rootNode().property("field"));
    }

    // ---- $ref handling ----

    @Test
    @DisplayName("a root $ref is followed when the root has no type/properties/items of its own")
    void rootRefFollowedWhenRootHasNoOwnKeywords() {
        JsonValue schema = schema("""
                {"$ref":"#/$defs/document","$defs":{"document":{"type":"object","properties":{"id":{"type":"string","x-dbtype":"ObjectId"}}}}}
                """);
        DatabaseTypedSchema typed = new DatabaseTypedSchema(schema);
        assertEquals(BsonType.OBJECT_ID, ((SchemaNode.Leaf) typed.rootNode().property("id")).bsonType());
    }

    @Test
    @DisplayName("a root $ref is ignored when the root also declares its own type/properties")
    void rootRefIgnoredWhenRootHasOwnKeywords() {
        JsonValue schema = schema("""
                {"$ref":"#/$defs/other","type":"object","properties":{"id":{"type":"string","x-dbtype":"ObjectId"}},
                 "$defs":{"other":{"type":"object","properties":{"nope":{"type":"string"}}}}}
                """);
        DatabaseTypedSchema typed = new DatabaseTypedSchema(schema);
        assertEquals(SchemaNode.UNTYPED, typed.rootNode().property("nope"));
        assertEquals(BsonType.OBJECT_ID, ((SchemaNode.Leaf) typed.rootNode().property("id")).bsonType());
    }

    @Test
    @DisplayName("a property that is only a $ref is followed to its target schema")
    void propertyRefFollowed() {
        JsonValue schema = schema("""
                {"type":"object","properties":{"id":{"$ref":"#/$defs/objectId"}},
                 "$defs":{"objectId":{"type":"string","x-dbtype":"ObjectId"}}}
                """);
        DatabaseTypedSchema typed = new DatabaseTypedSchema(schema);
        assertEquals(BsonType.OBJECT_ID, ((SchemaNode.Leaf) typed.rootNode().property("id")).bsonType());
    }

    @Test
    @DisplayName("an unresolvable $ref fails fast with a clear error")
    void unresolvableRefRejected() {
        JsonValue schema = schema("""
                {"type":"object","properties":{"id":{"$ref":"#/$defs/missing"}}}
                """);
        DatabaseTypedSchemaException exception = assertThrows(DatabaseTypedSchemaException.class, () -> new DatabaseTypedSchema(schema));
        assertContains(exception.getMessage(), "Unresolvable");
    }

    // ---- root shape ----

    @Test
    @DisplayName("a non-object root is rejected")
    void nonObjectRootRejected() {
        JsonValue schema = schema("""
                {"type":"array","items":{"type":"string"}}
                """);
        assertThrows(DatabaseTypedSchemaException.class, () -> new DatabaseTypedSchema(schema));
    }

    // ---- nested structures ----

    @Test
    @DisplayName("nested objects and arrays resolve their own leaves")
    void nestedStructuresResolveLeaves() {
        JsonValue schema = schema("""
                {"type":"object","properties":{
                    "address":{"type":"object","properties":{
                        "validUntil":{"type":"string","format":"date"}
                    }},
                    "tags":{"type":"array","items":{"type":"integer","format":"int32"}}
                }}
                """);
        DatabaseTypedSchema typed = new DatabaseTypedSchema(schema);

        SchemaNode.ObjectNode address = (SchemaNode.ObjectNode) typed.rootNode().property("address");
        assertEquals(BsonType.DATE, ((SchemaNode.Leaf) address.property("validUntil")).bsonType());

        SchemaNode.ArrayNode tags = (SchemaNode.ArrayNode) typed.rootNode().property("tags");
        assertEquals(BsonType.INT32, ((SchemaNode.Leaf) tags.items()).bsonType());
    }

    // ---- getJsonValue() debug view ----

    @Test
    @DisplayName("getJsonValue() annotates every leaf - defaulted and explicit alike - with its resolved x-dbtype")
    void getJsonValueAnnotatesEveryLeaf() {
        JsonValue schema = schema("""
                {"type":"object","properties":{
                    "id":{"type":"string","x-dbtype":"ObjectId"},
                    "name":{"type":"string"}
                }}
                """);
        DatabaseTypedSchema typed = new DatabaseTypedSchema(schema);
        JsonValue annotated = typed.getJsonValue();

        assertEquals("ObjectId", annotated.get(JsonArray.of("properties", "id", "x-dbtype")).getString());
        assertEquals("String", annotated.get(JsonArray.of("properties", "name", "x-dbtype")).getString());
    }

    // ---- helpers ----

    private static JsonValue schema(String json) {
        return JsonValueFactory.create(json);
    }

    private static JsonValue fieldSchema(String type, String format, String xDbType) {
        StringBuilder field = new StringBuilder("{\"type\":\"").append(type).append("\"");
        if (format != null && !format.isBlank()) {
            field.append(",\"format\":\"").append(format).append("\"");
        }
        if (xDbType != null) {
            field.append(",\"x-dbtype\":\"").append(xDbType).append("\"");
        }
        field.append("}");
        return schema("{\"type\":\"object\",\"properties\":{\"field\":" + field + "}}");
    }

    private static String resolvedXDbType(JsonValue schema) {
        DatabaseTypedSchema typed = new DatabaseTypedSchema(schema);
        return typed.getJsonValue().get(JsonArray.of("properties", "field", "x-dbtype")).getString();
    }

    private static void assertContains(String actual, String expectedSubstring) {
        org.junit.jupiter.api.Assertions.assertTrue(actual.contains(expectedSubstring),
                "Expected message to contain \"" + expectedSubstring + "\" but was: " + actual);
    }
}
