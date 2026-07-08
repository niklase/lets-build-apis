package com.zuunr.sqlschema;

import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValueFactory;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlUpsertGeneratorTest {

    private final SqlUpsertGenerator generator = new SqlUpsertGenerator();

    private JsonObject json(String s) {
        return JsonValueFactory.create(new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8))).getJsonObject();
    }

    @Test
    void flatDocument_mergeStatement() {
        JsonObject command = json("""
            {
              "insert": {
                "collection": "person",
                "documents": [{"id": "1", "name": "John", "age": 30}]
              }
            }
            """);

        String sql = generator.generateUpsert(command);

        assertTrue(sql.contains("MERGE person AS target"));
        assertTrue(sql.contains("ON target.id = source.id"));
        assertTrue(sql.contains("WHEN MATCHED THEN"));
        assertTrue(sql.contains("target.name = source.name"));
        assertTrue(sql.contains("WHEN NOT MATCHED THEN"));
        assertTrue(sql.contains("INSERT ("));
        assertTrue(sql.contains("VALUES (source."));
    }

    @Test
    void nestedObject_flattened() {
        JsonObject command = json("""
            {
              "insert": {
                "collection": "person",
                "documents": [{
                  "id": "1",
                  "address": {
                    "street": "123 Main St",
                    "city": "Springfield"
                  }
                }]
              }
            }
            """);

        String sql = generator.generateUpsert(command);

        assertTrue(sql.contains("address__street"));
        assertTrue(sql.contains("address__city"));
        assertTrue(sql.contains("'123 Main St'"));
        assertTrue(sql.contains("'Springfield'"));
    }

    @Test
    void arrayOfScalars_deleteAndInsertAuxTable() {
        JsonObject command = json("""
            {
              "insert": {
                "collection": "person",
                "documents": [{
                  "id": "1",
                  "name": "John",
                  "tags": ["java", "sql"]
                }]
              }
            }
            """);

        String sql = generator.generateUpsert(command);

        assertTrue(sql.contains("DELETE FROM person_tags WHERE person_id = '1'"));
        assertTrue(sql.contains("INSERT INTO person_tags (person_id, position, value) VALUES ('1', 0, 'java')"));
        assertTrue(sql.contains("INSERT INTO person_tags (person_id, position, value) VALUES ('1', 1, 'sql')"));
    }

    @Test
    void arrayOfObjects_auxiliaryTableStatements() {
        JsonObject command = json("""
            {
              "insert": {
                "collection": "person",
                "documents": [{
                  "id": "1",
                  "addresses": [
                    {"street": "123 Main St", "city": "Springfield"},
                    {"street": "456 Oak Ave", "city": "Shelbyville"}
                  ]
                }]
              }
            }
            """);

        String sql = generator.generateUpsert(command);

        assertTrue(sql.contains("DELETE FROM person_addresses WHERE person_id = '1'"));
        assertTrue(sql.contains("'123 Main St'"));
        assertTrue(sql.contains("'456 Oak Ave'"));
    }

    @Test
    void multipleDocuments_separateMerges() {
        JsonObject command = json("""
            {
              "insert": {
                "collection": "person",
                "documents": [
                  {"id": "1", "name": "Alice"},
                  {"id": "2", "name": "Bob"}
                ]
              }
            }
            """);

        String sql = generator.generateUpsert(command);

        assertTrue(sql.contains("'Alice'"));
        assertTrue(sql.contains("'Bob'"));
    }

    @Test
    void singleQuoteInString_escaped() {
        JsonObject command = json("""
            {
              "insert": {
                "collection": "person",
                "documents": [{"id": "1", "name": "O'Brien"}]
              }
            }
            """);

        String sql = generator.generateUpsert(command);

        assertTrue(sql.contains("'O''Brien'"));
    }
}
