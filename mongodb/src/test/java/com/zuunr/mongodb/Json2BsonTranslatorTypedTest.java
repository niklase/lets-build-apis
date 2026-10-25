package com.zuunr.mongodb;

import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import com.zuunr.json.JsonValueFactory;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Json2BsonTranslatorTypedTest {

    private static final JsonValue SCHEMA = JsonValueFactory.create("""
            {
              "type": "object",
              "properties": {
                "id":              {"type": "string", "x-dbtype": "ObjectId"},
                "name":            {"type": "string"},
                "validUntil":      {"type": "string", "format": "date"},
                "lastSeenAt":      {"type": "string", "format": "date-time"},
                "probability":     {"type": "number"},
                "ratio":           {"type": "number", "format": "float"},
                "age":             {"type": "integer", "format": "int32"},
                "views":           {"type": "integer"},
                "active":          {"type": "boolean"},
                "nickname":        {"type": ["string", "null"]},
                "address": {
                  "type": "object",
                  "properties": {
                    "validUntil": {"type": "string", "format": "date"}
                  }
                },
                "scores": {
                  "type": "array",
                  "items": {"type": "integer", "format": "int32"}
                }
              }
            }
            """);

    private static final DatabaseTypedSchema TYPED_SCHEMA = SCHEMA.as(DatabaseTypedSchema.class);

    @Test
    @DisplayName("a string field with x-dbtype ObjectId translates directly to org.bson.types.ObjectId")
    void objectIdLeaf() {
        ObjectId objectId = new ObjectId();
        Document doc = translate("{\"id\": \"" + objectId.toHexString() + "\"}");
        assertEquals(objectId, doc.get("id"));
        assertTrue(doc.get("id") instanceof ObjectId);
    }

    @Test
    @DisplayName("a plain string field translates to java.lang.String")
    void stringLeaf() {
        Document doc = translate("{\"name\": \"Ada\"}");
        assertEquals("Ada", doc.get("name"));
    }

    @Test
    @DisplayName("format: date translates to java.util.Date at UTC midnight")
    void dateOnlyLeaf() {
        Document doc = translate("{\"validUntil\": \"2024-03-15\"}");
        assertEquals(Date.from(Instant.parse("2024-03-15T00:00:00Z")), doc.get("validUntil"));
    }

    @Test
    @DisplayName("format: date-time translates to java.util.Date preserving the time")
    void dateTimeLeaf() {
        Document doc = translate("{\"lastSeenAt\": \"2024-03-15T10:30:00Z\"}");
        assertEquals(Date.from(Instant.parse("2024-03-15T10:30:00Z")), doc.get("lastSeenAt"));
    }

    @Test
    @DisplayName("a bare number field defaults to Decimal128")
    void numberDefaultsToDecimal128() {
        Document doc = translate("{\"probability\": 0.375}");
        assertEquals(new Decimal128(new BigDecimal("0.375")), doc.get("probability"));
    }

    @Test
    @DisplayName("format: float on a number field maps to Double")
    void numberFloatMapsToDouble() {
        Document doc = translate("{\"ratio\": 1.5}");
        assertEquals(1.5d, doc.get("ratio"));
        assertTrue(doc.get("ratio") instanceof Double);
    }

    @Test
    @DisplayName("format: int32 on an integer field maps to a 32-bit int")
    void int32Leaf() {
        Document doc = translate("{\"age\": 42}");
        assertEquals(42, doc.get("age"));
        assertTrue(doc.get("age") instanceof Integer);
    }

    @Test
    @DisplayName("a bare integer field defaults to 64-bit long")
    void integerDefaultsToInt64() {
        Document doc = translate("{\"views\": 42}");
        assertEquals(42L, doc.get("views"));
        assertTrue(doc.get("views") instanceof Long);
    }

    @Test
    @DisplayName("an integer value that overflows Int32 fails fast instead of silently truncating")
    void int32OverflowFailsFast() {
        assertThrows(IllegalArgumentException.class, () -> translate("{\"age\": 5000000000}"));
    }

    @Test
    @DisplayName("boolean fields translate to java.lang.Boolean")
    void booleanLeaf() {
        Document doc = translate("{\"active\": true}");
        assertEquals(true, doc.get("active"));
    }

    @Test
    @DisplayName("null always translates to BSON null, independent of the field's x-dbtype")
    void nullAlwaysPassesThrough() {
        Document doc = translate("{\"nickname\": null}");
        assertTrue(doc.containsKey("nickname"));
        assertNull(doc.get("nickname"));
    }

    @Test
    @DisplayName("a nested object resolves its own properties' leaves")
    void nestedObject() {
        Document doc = translate("{\"address\": {\"validUntil\": \"2024-01-01\"}}");
        Document address = (Document) doc.get("address");
        assertEquals(Date.from(Instant.parse("2024-01-01T00:00:00Z")), address.get("validUntil"));
    }

    @Test
    @DisplayName("array elements resolve against the array's \"items\" schema")
    void arrayItems() {
        Document doc = translate("{\"scores\": [1, 2, 3]}");
        assertEquals(List.of(1, 2, 3), doc.get("scores"));
        assertTrue(((List<?>) doc.get("scores")).get(0) instanceof Integer);
    }

    @Test
    @DisplayName("a field not declared in the schema falls back to the untyped, runtime-type-based translation")
    void undeclaredFieldFallsBackToUntyped() {
        Document doc = translate("{\"undeclared\": 7}");
        assertEquals(7L, doc.get("undeclared"));
    }

    private static Document translate(String json) {
        JsonObject jsonObject = JsonValueFactory.create(json).getJsonObject();
        return Json2BsonTranslator.translate(jsonObject, TYPED_SCHEMA);
    }
}
