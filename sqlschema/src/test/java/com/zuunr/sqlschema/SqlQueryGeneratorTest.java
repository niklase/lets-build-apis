package com.zuunr.sqlschema;

import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValueFactory;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlQueryGeneratorTest {

    private final SqlQueryGenerator generator = new SqlQueryGenerator();

    private JsonObject json(String s) {
        return JsonValueFactory.create(new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8))).getJsonObject();
    }

    @Test
    void simpleEquality() {
        JsonObject find = json("""
            {
              "collection": "person",
              "filter": {"name": [{"$eq": "John"}]}
            }
            """);

        assertEquals("SELECT * FROM person WHERE name = 'John'", generator.generateSelect(find));
    }

    @Test
    void numericComparisons() {
        JsonObject find = json("""
            {
              "collection": "person",
              "filter": {"age": [{"$gt": 18}, {"$lte": 65}]}
            }
            """);

        String sql = generator.generateSelect(find);
        assertTrue(sql.contains("(age > 18 AND age <= 65)"));
    }

    @Test
    void inOperator() {
        JsonObject find = json("""
            {
              "collection": "person",
              "filter": {"status": [{"$in": ["active", "pending"]}]}
            }
            """);

        String sql = generator.generateSelect(find);
        assertTrue(sql.contains("status IN ('active', 'pending')"));
    }

    @Test
    void andOperator() {
        JsonObject find = json("""
            {
              "collection": "person",
              "filter": {"$and": [
                {"age":  [{"$gt": 18}]},
                {"name": [{"$ne": "Bot"}]}
              ]}
            }
            """);

        String sql = generator.generateSelect(find);
        assertTrue(sql.contains("(age > 18 AND name <> 'Bot')"));
    }

    @Test
    void orOperator() {
        JsonObject find = json("""
            {
              "collection": "person",
              "filter": {"$or": [
                {"role": [{"$eq": "admin"}]},
                {"role": [{"$eq": "moderator"}]}
              ]}
            }
            """);

        String sql = generator.generateSelect(find);
        assertTrue(sql.contains("(role = 'admin' OR role = 'moderator')"));
    }

    @Test
    void nestedFieldPath_mappedToDoubleUnderscore() {
        JsonObject find = json("""
            {
              "collection": "person",
              "filter": {"address.city": [{"$eq": "Springfield"}]}
            }
            """);

        String sql = generator.generateSelect(find);
        assertTrue(sql.contains("address__city = 'Springfield'"));
    }

    @Test
    void sortLimitOffset() {
        JsonObject find = json("""
            {
              "collection": "person",
              "filter": {"age": [{"$gte": 18}]},
              "sort":   [{"name": 1}, {"age": -1}],
              "limit":  10,
              "skip":   20
            }
            """);

        String sql = generator.generateSelect(find);
        assertTrue(sql.contains("ORDER BY name ASC, age DESC"));
        assertTrue(sql.contains("OFFSET 20 ROWS FETCH NEXT 10 ROWS ONLY"));
    }

    @Test
    void noFilter_noWhereClause() {
        JsonObject find = json("""
            {"collection": "person"}
            """);

        assertEquals("SELECT * FROM person", generator.generateSelect(find));
    }

    @Test
    void sqlInjection_stringLiteralEscaped() {
        JsonObject find = json("""
            {
              "collection": "person",
              "filter": {"name": [{"$eq": "O'Brien"}]}
            }
            """);

        String sql = generator.generateSelect(find);
        assertTrue(sql.contains("name = 'O''Brien'"));
    }
}
