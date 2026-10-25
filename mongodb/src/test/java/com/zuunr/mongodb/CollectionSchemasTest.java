package com.zuunr.mongodb;

import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValueFactory;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CollectionSchemasTest {

    private static final JsonObject CONFIG = JsonValueFactory.create("""
            {
              "collectionSchemas": {
                "people": {
                  "type": "object",
                  "properties": {
                    "id":         {"type": "string", "x-dbtype": "ObjectId"},
                    "age":        {"type": "integer", "format": "int32"},
                    "views":      {"type": "integer"},
                    "validUntil": {"type": "string", "format": "date"},
                    "address": {
                      "type": "object",
                      "properties": {
                        "city": {"type": "string"}
                      }
                    }
                  }
                }
              }
            }
            """).getJsonObject();

    @Test
    @DisplayName("a document for a registered collection is translated against its schema")
    void registeredCollectionUsesItsSchema() {
        CollectionSchemas schemas = new CollectionSchemas(CONFIG);
        ObjectId id = new ObjectId();

        Document doc = schemas.translateDocument("people", document("{\"id\": \"" + id.toHexString() + "\", \"age\": 42}"));

        assertEquals(id, doc.get("id"));
        assertTrue(doc.get("age") instanceof Integer);
    }

    @Test
    @DisplayName("a document for an unregistered collection falls back to the default, untyped mapping")
    void unregisteredCollectionFallsBackToDefault() {
        CollectionSchemas schemas = new CollectionSchemas(CONFIG);

        Document doc = schemas.translateDocument("unregistered", document("{\"age\": 42}"));

        assertTrue(doc.get("age") instanceof Long); // default mapping for a bare integer: Int64
    }

    @Test
    @DisplayName("calling translateDocument repeatedly for an unregistered collection doesn't fail (warn-once bookkeeping is safe to repeat)")
    void repeatedCallsForUnregisteredCollectionAreSafe() {
        CollectionSchemas schemas = new CollectionSchemas(CONFIG);
        assertDoesNotThrow(() -> {
            schemas.translateDocument("unregistered", document("{\"age\": 1}"));
            schemas.translateDocument("unregistered", document("{\"age\": 2}"));
        });
    }

    @Test
    @DisplayName("$set coerces each field's value against the collection's schema")
    void setCoercesEachFieldAgainstTheSchema() {
        CollectionSchemas schemas = new CollectionSchemas(CONFIG);
        ObjectId id = new ObjectId();

        Document doc = schemas.translateDocument("people",
                document("{\"$set\": {\"id\": \"" + id.toHexString() + "\", \"age\": 42}}"));

        Document setClause = (Document) doc.get("$set");
        assertEquals(id, setClause.get("id"));
        assertTrue(setClause.get("age") instanceof Integer); // x-dbtype Int32, not the default Int64
    }

    @Test
    @DisplayName("$inc coerces its field's value against the collection's schema")
    void incCoercesAgainstTheSchema() {
        CollectionSchemas schemas = new CollectionSchemas(CONFIG);

        Document doc = schemas.translateDocument("people", document("{\"$inc\": {\"age\": 1}}"));

        Document incClause = (Document) doc.get("$inc");
        assertTrue(incClause.get("age") instanceof Integer);
    }

    @Test
    @DisplayName("$set accepts a dotted path into a nested object field")
    void setCoercesADottedPath() {
        CollectionSchemas schemas = new CollectionSchemas(CONFIG);

        Document doc = schemas.translateDocument("people", document("{\"$set\": {\"address.city\": \"NYC\"}}"));

        assertEquals("NYC", ((Document) doc.get("$set")).get("address.city"));
    }

    @Test
    @DisplayName("$set on a field not in the schema falls back to the default mapping for that field")
    void setOnUndeclaredFieldFallsBackToDefault() {
        CollectionSchemas schemas = new CollectionSchemas(CONFIG);

        Document doc = schemas.translateDocument("people", document("{\"$set\": {\"undeclared\": 7}}"));

        assertTrue(((Document) doc.get("$set")).get("undeclared") instanceof Long);
    }

    @Test
    @DisplayName("a modifier operator other than $set/$inc (e.g. $unset) always keeps the default mapping")
    void otherOperatorsKeepTheDefaultMapping() {
        CollectionSchemas schemas = new CollectionSchemas(CONFIG);

        // "age" is x-dbtype Int32, but $unset's value is a meaningless placeholder Mongo ignores -
        // it must never be coerced as if it were a value of the field's own type.
        Document doc = schemas.translateDocument("people", document("{\"$unset\": {\"age\": \"\"}}"));

        assertEquals("", ((Document) doc.get("$unset")).get("age"));
    }

    @Test
    @DisplayName("a modifier document combining multiple operators translates each independently")
    void multipleOperatorsInOneModifierDocument() {
        CollectionSchemas schemas = new CollectionSchemas(CONFIG);

        Document doc = schemas.translateDocument("people", document("""
                {"$set": {"validUntil": "2024-01-01"}, "$inc": {"views": 1}, "$unset": {"address": ""}}
                """));

        assertTrue(((Document) doc.get("$set")).get("validUntil") instanceof java.util.Date);
        assertTrue(((Document) doc.get("$inc")).get("views") instanceof Long); // "views" has no format -> default Int64
        assertEquals("", ((Document) doc.get("$unset")).get("address"));
    }

    @Test
    @DisplayName("$set on an unregistered collection falls back to the default mapping")
    void setOnUnregisteredCollectionFallsBackToDefault() {
        CollectionSchemas schemas = new CollectionSchemas(CONFIG);

        Document doc = schemas.translateDocument("unregistered", document("{\"$set\": {\"age\": 7}}"));

        assertTrue(((Document) doc.get("$set")).get("age") instanceof Long);
    }

    @Test
    @DisplayName("a missing/null collection name falls back to the default mapping instead of failing")
    void nullCollectionFallsBackToDefault() {
        CollectionSchemas schemas = new CollectionSchemas(CONFIG);
        assertDoesNotThrow(() -> schemas.translateDocument(null, document("{\"age\": 1}")));
    }

    @Test
    @DisplayName("no \"collectionSchemas\" key at all is treated as no registered collections")
    void missingCollectionSchemasKeyIsFine() {
        CollectionSchemas schemas = new CollectionSchemas(JsonObject.EMPTY);
        Document doc = schemas.translateDocument("anything", document("{\"age\": 1}"));
        assertTrue(doc.get("age") instanceof Long);
    }

    @Test
    @DisplayName("an invalid schema in collectionSchemas fails fast when CollectionSchemas is built, not on first use")
    void invalidSchemaFailsAtConstructionTime() {
        JsonObject badConfig = JsonValueFactory.create("""
                {"collectionSchemas": {"people": {"type": "object", "properties": {"age": {"type": "integer", "x-dbtype": "Date"}}}}}
                """).getJsonObject();

        assertThrows(DatabaseTypedSchemaException.class, () -> new CollectionSchemas(badConfig));
    }

    private static JsonObject document(String json) {
        return JsonValueFactory.create(json).getJsonObject();
    }
}
