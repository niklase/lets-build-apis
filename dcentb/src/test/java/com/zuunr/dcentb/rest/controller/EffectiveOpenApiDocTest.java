package com.zuunr.dcentb.rest.controller;

import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonValue;
import com.zuunr.json.JsonValueFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Covers EffectiveOpenApiDoc's resolution of "x-dcentb.collections.&lt;name&gt;.dbSchema"/
 * "dbCollection" into "x-dcentb.mongodb.collectionSchemas"/per-operation "collection" - see
 * mongodb-types-from-schema.md and com.zuunr.mongodb.DatabaseTypedSchema.
 */
class EffectiveOpenApiDocTest {

    @Test
    @DisplayName("a $ref'd dbSchema resolves into collectionSchemas, keyed by the default dbCollection (the collection's own name)")
    void refDbSchemaResolvesUnderDefaultDbCollection() {
        JsonValue doc = JsonValueFactory.create("""
                {
                  "paths": {"/students": {"get": {}}},
                  "x-dcentb": {"collections": {"students": {"dbSchema": {"$ref": "#/components/schemas/Students_item_v1"}}}},
                  "components": {"schemas": {"Students_item_v1": {"type": "object", "properties": {"name": {"type": "string"}}}}}
                }
                """);

        JsonValue effective = doc.as(EffectiveOpenApiDoc.class).getJsonValue();
        JsonValue schema = collectionSchema(effective, "students");

        assertEquals("object", schema.get("type").getString());
        assertEquals("string", schema.get(JsonArray.of("properties", "name", "type")).getString());
    }

    @Test
    @DisplayName("an inline dbSchema (no $ref) resolves the same way")
    void inlineDbSchemaResolves() {
        JsonValue doc = JsonValueFactory.create("""
                {
                  "paths": {"/teachers": {"get": {}}},
                  "x-dcentb": {"collections": {"teachers": {"dbSchema": {"type": "object", "properties": {"name": {"type": "string"}}}}}},
                  "components": {"schemas": {}}
                }
                """);

        JsonValue effective = doc.as(EffectiveOpenApiDoc.class).getJsonValue();
        JsonValue schema = collectionSchema(effective, "teachers");

        assertEquals("string", schema.get(JsonArray.of("properties", "name", "type")).getString());
    }

    @Test
    @DisplayName("an explicit dbCollection changes both the collectionSchemas key and the per-operation \"collection\"")
    void explicitDbCollectionIsUsedAsTheKey() {
        JsonValue doc = JsonValueFactory.create("""
                {
                  "paths": {"/students": {"get": {}}},
                  "x-dcentb": {"collections": {"students": {
                      "dbCollection": "students_v2",
                      "dbSchema": {"type": "object", "properties": {"name": {"type": "string"}}}
                  }}},
                  "components": {"schemas": {}}
                }
                """);

        JsonValue effective = doc.as(EffectiveOpenApiDoc.class).getJsonValue();

        assertNull(effective.get(JsonArray.of("x-dcentb", "mongodb", "collectionSchemas", "students")));
        assertEquals("string", collectionSchema(effective, "students_v2").get(JsonArray.of("properties", "name", "type")).getString());
        assertEquals("students_v2", effective.get(JsonArray.of("paths", "/students", "get", "x-dcentb", "mongodb", "collection")).getString());
        assertEquals("students_v2", effective.get(JsonArray.of("x-dcentb", "collections", "students", "dbCollection")).getString());
    }

    @Test
    @DisplayName("dbCollection defaults to the collection's own name and is written back")
    void dbCollectionDefaultsAndIsWrittenBack() {
        JsonValue doc = JsonValueFactory.create("""
                {
                  "paths": {"/students": {"get": {}}},
                  "x-dcentb": {"collections": {"students": {"dbSchema": {}}}},
                  "components": {"schemas": {}}
                }
                """);

        JsonValue effective = doc.as(EffectiveOpenApiDoc.class).getJsonValue();
        assertEquals("students", effective.get(JsonArray.of("x-dcentb", "collections", "students", "dbCollection")).getString());
    }

    @Test
    @DisplayName("every operation on a collection's paths gets the same resolved \"collection\", regardless of what (if anything) it previously declared")
    void everyOperationGetsTheResolvedCollection() {
        JsonValue doc = JsonValueFactory.create("""
                {
                  "paths": {
                    "/students": {"get": {}, "post": {"x-dcentb": {"mongodb": {"collection": "ignored-stray-value"}}}},
                    "/students/{id}": {"get": {}}
                  },
                  "x-dcentb": {"collections": {"students": {"dbCollection": "actual_students", "dbSchema": {}}}},
                  "components": {"schemas": {}}
                }
                """);

        JsonValue effective = doc.as(EffectiveOpenApiDoc.class).getJsonValue();

        assertEquals("actual_students", effective.get(JsonArray.of("paths", "/students", "get", "x-dcentb", "mongodb", "collection")).getString());
        assertEquals("actual_students", effective.get(JsonArray.of("paths", "/students", "post", "x-dcentb", "mongodb", "collection")).getString());
        assertEquals("actual_students", effective.get(JsonArray.of("paths", "/students/{id}", "get", "x-dcentb", "mongodb", "collection")).getString());
    }

    @Test
    @DisplayName("a top-level \"_id\" is synthesized, mirroring \"meta.id\"'s own resolved schema")
    void idMirrorsMetaIdsSchema() {
        JsonValue doc = JsonValueFactory.create("""
                {
                  "paths": {"/students": {"get": {}}},
                  "x-dcentb": {"collections": {"students": {"dbSchema": {"$ref": "#/components/schemas/Students_item_v1"}}}},
                  "components": {
                    "schemas": {
                      "Students_item_v1": {"type": "object", "properties": {"meta": {"$ref": "#/components/schemas/Meta_item_v1"}}},
                      "Meta_item_v1": {"type": "object", "properties": {"id": {"type": "string", "x-dbtype": "String"}}}
                    }
                  }
                }
                """);

        JsonValue effective = doc.as(EffectiveOpenApiDoc.class).getJsonValue();
        JsonValue schema = collectionSchema(effective, "students");

        // Mirrored from Meta_item_v1.properties.id, reached through the "meta" $ref and then
        // the attached "components" - proving the nested-ref resolution still works end to end.
        assertEquals("String", schema.get(JsonArray.of("properties", "_id", "x-dbtype")).getString());
    }

    @Test
    @DisplayName("dbSchema: true opts a collection out of typing - no collectionSchemas entry, but dbCollection still resolves")
    void dbSchemaTrueOptsOut() {
        JsonValue doc = JsonValueFactory.create("""
                {
                  "paths": {"/students": {"get": {}}},
                  "x-dcentb": {"collections": {"students": {"dbSchema": true}}},
                  "components": {"schemas": {}}
                }
                """);

        JsonValue effective = doc.as(EffectiveOpenApiDoc.class).getJsonValue();

        assertNull(effective.get(JsonArray.of("x-dcentb", "mongodb", "collectionSchemas", "students")));
        assertEquals("students", effective.get(JsonArray.of("paths", "/students", "get", "x-dcentb", "mongodb", "collection")).getString());
        assertEquals("students", effective.get(JsonArray.of("x-dcentb", "collections", "students", "dbCollection")).getString());
    }

    @Test
    @DisplayName("dbSchema: {} opts a collection out the same way as true")
    void dbSchemaEmptyObjectOptsOut() {
        JsonValue doc = JsonValueFactory.create("""
                {
                  "paths": {"/students": {"get": {}}},
                  "x-dcentb": {"collections": {"students": {"dbSchema": {}}}},
                  "components": {"schemas": {}}
                }
                """);

        JsonValue effective = doc.as(EffectiveOpenApiDoc.class).getJsonValue();
        assertNull(effective.get(JsonArray.of("x-dcentb", "mongodb", "collectionSchemas", "students")));
    }

    @Test
    @DisplayName("a missing \"x-dcentb.collections.<name>\" block fails fast")
    void missingCollectionsBlockFailsFast() {
        JsonValue doc = JsonValueFactory.create("""
                {"paths": {"/widgets": {"get": {}}}, "x-dcentb": {}, "components": {"schemas": {}}}
                """);

        // Constructed directly rather than via doc.as(EffectiveOpenApiDoc.class): as() wraps
        // whatever the constructor throws inside a reflection InvocationTargetException inside
        // a RuntimeException, which would hide the IllegalStateException's real type here.
        assertThrows(IllegalStateException.class, () -> new EffectiveOpenApiDoc(doc));
    }

    @Test
    @DisplayName("a \"x-dcentb.collections.<name>\" block with no \"dbSchema\" at all fails fast")
    void missingDbSchemaFailsFast() {
        JsonValue doc = JsonValueFactory.create("""
                {"paths": {"/widgets": {"get": {}}}, "x-dcentb": {"collections": {"widgets": {"permissions": {}}}}, "components": {"schemas": {}}}
                """);

        assertThrows(IllegalStateException.class, () -> new EffectiveOpenApiDoc(doc));
    }

    @Test
    @DisplayName("a dangling $ref in dbSchema fails fast")
    void danglingDbSchemaRefFailsFast() {
        JsonValue doc = JsonValueFactory.create("""
                {"paths": {"/widgets": {"get": {}}}, "x-dcentb": {"collections": {"widgets": {"dbSchema": {"$ref": "#/components/schemas/DoesNotExist"}}}}, "components": {"schemas": {}}}
                """);

        assertThrows(IllegalStateException.class, () -> new EffectiveOpenApiDoc(doc));
    }

    @Test
    @DisplayName("two collections resolving to the same dbCollection, both with a real dbSchema, fail fast")
    void duplicateDbCollectionWithBothRealFailsFast() {
        JsonValue doc = JsonValueFactory.create("""
                {
                  "paths": {"/students": {"get": {}}, "/pupils": {"get": {}}},
                  "x-dcentb": {"collections": {
                      "students": {"dbCollection": "same_name", "dbSchema": {"type": "object", "properties": {}}},
                      "pupils":   {"dbCollection": "same_name", "dbSchema": {"type": "object", "properties": {}}}
                  }},
                  "components": {"schemas": {}}
                }
                """);

        assertThrows(IllegalStateException.class, () -> new EffectiveOpenApiDoc(doc));
    }

    @Test
    @DisplayName("two collections resolving to the same dbCollection is fine when one of them is opted out")
    void duplicateDbCollectionWithOneOptedOutIsFine() {
        JsonValue doc = JsonValueFactory.create("""
                {
                  "paths": {"/students": {"get": {}}, "/pupils": {"get": {}}},
                  "x-dcentb": {"collections": {
                      "students": {"dbCollection": "same_name", "dbSchema": {"type": "object", "properties": {}}},
                      "pupils":   {"dbCollection": "same_name", "dbSchema": true}
                  }},
                  "components": {"schemas": {}}
                }
                """);

        JsonValue effective = doc.as(EffectiveOpenApiDoc.class).getJsonValue();
        assertEquals("object", collectionSchema(effective, "same_name").get("type").getString());
    }

    private static JsonValue collectionSchema(JsonValue effectiveDoc, String dbCollection) {
        return effectiveDoc.get(JsonArray.of("x-dcentb", "mongodb", "collectionSchemas", dbCollection));
    }
}
