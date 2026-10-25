package com.zuunr.mongodb;

import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValueFactory;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Json2BsonQueryTranslatorTypedTest {

    private static final CollectionSchemas COLLECTION_SCHEMAS = new CollectionSchemas(JsonValueFactory.create("""
            {
              "collectionSchemas": {
                "people": {
                  "type": "object",
                  "properties": {
                    "id":         {"type": "string", "x-dbtype": "ObjectId"},
                    "name":       {"type": "string"},
                    "validUntil": {"type": "string", "format": "date"},
                    "age":        {"type": "integer", "format": "int32"},
                    "nickname":   {"type": ["string", "null"]},
                    "tags":       {"type": "array", "items": {"type": "integer", "format": "int32"}}
                  }
                }
              }
            }
            """).getJsonObject());

    private static final Json2BsonQueryTranslator TRANSLATOR = new Json2BsonQueryTranslator(COLLECTION_SCHEMAS);

    @Test
    @DisplayName("a field criterion against a registered collection coerces the operand to the field's BSON type")
    void fieldCriterionIsCoercedToFieldType() {
        ObjectId id = new ObjectId();
        Document filter = translate("people", "{\"id\": [{\"$eq\": \"" + id.toHexString() + "\"}]}");

        Document idCriteria = (Document) filter.get("id");
        assertEquals(id, idCriteria.get("$eq"));
        assertTrue(idCriteria.get("$eq") instanceof ObjectId);
    }

    @Test
    @DisplayName("$gt/$lt on a format: date field coerce to java.util.Date")
    void rangeOperatorsOnDateField() {
        Document filter = translate("people", "{\"validUntil\": [{\"$gte\": \"2024-01-01\"}, {\"$lt\": \"2025-01-01\"}]}");

        Document criteria = (Document) filter.get("validUntil");
        assertEquals(Date.from(Instant.parse("2024-01-01T00:00:00Z")), criteria.get("$gte"));
        assertEquals(Date.from(Instant.parse("2025-01-01T00:00:00Z")), criteria.get("$lt"));
    }

    @Test
    @DisplayName("$in/$nin coerce every candidate individually against the field's type")
    void inAndNinCoerceEachElement() {
        Document filter = translate("people", "{\"age\": [{\"$in\": [1, 2, 3]}]}");

        Document criteria = (Document) filter.get("age");
        List<?> in = (List<?>) criteria.get("$in");
        assertEquals(List.of(1, 2, 3), in);
        assertTrue(in.get(0) instanceof Integer); // x-dbtype Int32, not the default Int64
    }

    @Test
    @DisplayName("$in/$nin on an ObjectId field coerce every candidate to a real ObjectId, not just $eq")
    void inAndNinCoerceEachElementOnObjectIdField() {
        ObjectId first = new ObjectId();
        ObjectId second = new ObjectId();

        Document filter = translate("people", "{\"id\": [{\"$in\": [\"" + first.toHexString() + "\", \"" + second.toHexString() + "\"]}]}");

        List<?> in = (List<?>) ((Document) filter.get("id")).get("$in");
        assertEquals(List.of(first, second), in);
        assertTrue(in.get(0) instanceof ObjectId);

        Document ninFilter = translate("people", "{\"id\": [{\"$nin\": [\"" + first.toHexString() + "\"]}]}");
        List<?> nin = (List<?>) ((Document) ninFilter.get("id")).get("$nin");
        assertEquals(List.of(first), nin);
        assertTrue(nin.get(0) instanceof ObjectId);
    }

    @Test
    @DisplayName("a null candidate in $in passes through as BSON null, same as elsewhere")
    void nullInsideInPassesThrough() {
        Document filter = translate("people", "{\"nickname\": [{\"$in\": [\"Al\", null]}]}");

        List<?> in = (List<?>) ((Document) filter.get("nickname")).get("$in");
        assertEquals("Al", in.get(0));
        assertNull(in.get(1));
    }

    @Test
    @DisplayName("querying a scalar against an array field coerces against the array's item type")
    void scalarAgainstArrayFieldUsesItemType() {
        Document filter = translate("people", "{\"tags\": [{\"$eq\": 7}]}");

        Document criteria = (Document) filter.get("tags");
        assertTrue(criteria.get("$eq") instanceof Integer);
    }

    @Test
    @DisplayName("an operator not in the comparison set (e.g. $exists) is never coerced against the field's type")
    void nonComparisonOperatorIsNotCoerced() {
        Document filter = translate("people", "{\"id\": [{\"$exists\": true}]}");

        Document criteria = (Document) filter.get("id");
        assertEquals(true, criteria.get("$exists")); // would otherwise crash trying to parse `true` as an ObjectId
    }

    @Test
    @DisplayName("a field not declared in the schema falls back to the default, untyped translation")
    void undeclaredFieldFallsBackToDefault() {
        Document filter = translate("people", "{\"undeclared\": [{\"$eq\": 7}]}");

        Document criteria = (Document) filter.get("undeclared");
        assertTrue(criteria.get("$eq") instanceof Long); // default mapping for a bare integer
    }

    @Test
    @DisplayName("an unregistered collection falls back to the default, untyped translation")
    void unregisteredCollectionFallsBackToDefault() {
        Document filter = translate("unregistered", "{\"age\": [{\"$eq\": 7}]}");

        Document criteria = (Document) filter.get("age");
        assertTrue(criteria.get("$eq") instanceof Long);
    }

    @Test
    @DisplayName("logical operators ($and/$or) keep resolving nested field criteria against the same collection's schema")
    void logicalOperatorsPreserveSchemaContext() {
        Document filter = translate("people", """
                {"$and": [
                    {"age": [{"$gt": 18}]},
                    {"name": [{"$eq": "Ada"}]}
                ]}
                """);

        List<?> clauses = (List<?>) filter.get("$and");
        Document ageClause = (Document) ((Document) clauses.get(0)).get("age");
        assertTrue(ageClause.get("$gt") instanceof Integer);
    }

    private static Document translate(String collection, String query) {
        JsonObject jsonQuery = JsonValueFactory.create(query).getJsonObject();
        return TRANSLATOR.translateQuery(collection, jsonQuery);
    }
}
