package com.zuunr.sqlschema;

import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValueFactory;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlywayMigrationGeneratorTest {

    private final FlywayMigrationGenerator generator = new FlywayMigrationGenerator();

    private JsonObject json(String s) {
        return JsonValueFactory.create(new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8))).getJsonObject();
    }

    @Test
    void flatSchema_rootTable() {
        JsonObject schema = json("""
            {
              "type": "object",
              "properties": {
                "id":     {"type": "string"},
                "name":   {"type": "string"},
                "age":    {"type": "integer"},
                "score":  {"type": "number"},
                "active": {"type": "boolean"}
              },
              "required": ["id", "name"]
            }
            """);

        String sql = generator.generateCreateTable("person", schema);

        assertTrue(sql.contains("CREATE TABLE person ("));
        assertTrue(sql.contains("id NVARCHAR(255) NOT NULL"));
        // All non-root fields are nullable to support absent JSON properties mapping to NULL
        assertTrue(sql.contains("name NVARCHAR(255)"));
        assertTrue(sql.contains("age BIGINT"));
        assertTrue(sql.contains("score FLOAT"));
        assertTrue(sql.contains("active BIT"));
        assertTrue(sql.contains("PRIMARY KEY (id)"));
        // No FK on root table
        assertFalse(sql.contains("FOREIGN KEY"));
    }

    @Test
    void nestedObject_flattenedIntoRootTable() {
        JsonObject schema = json("""
            {
              "type": "object",
              "properties": {
                "id": {"type": "string"},
                "address": {
                  "type": "object",
                  "properties": {
                    "street": {"type": "string"},
                    "city":   {"type": "string"}
                  }
                }
              }
            }
            """);

        String sql = generator.generateCreateTable("person", schema);

        // Nested object fields are flattened into the root table, no extra table
        assertTrue(sql.contains("address__street NVARCHAR(255)"));
        assertTrue(sql.contains("address__city NVARCHAR(255)"));
        assertFalse(sql.contains("CREATE TABLE person_address"));
    }

    @Test
    void arrayOfScalars_childTableWithFk() {
        JsonObject schema = json("""
            {
              "type": "object",
              "properties": {
                "id":   {"type": "string"},
                "tags": {"type": "array", "items": {"type": "string"}}
              }
            }
            """);

        String sql = generator.generateCreateTable("person", schema);

        // Root table: no tags column (only appears as part of the child table name)
        assertFalse(sql.contains("\n    tags "));

        // Child table
        assertTrue(sql.contains("CREATE TABLE person_tags ("));
        assertTrue(sql.contains("person_id NVARCHAR(255) NOT NULL"));
        assertTrue(sql.contains("position INTEGER NOT NULL"));
        assertTrue(sql.contains("value NVARCHAR(255)"));
        assertTrue(sql.contains("PRIMARY KEY (person_id, position)"));
        assertTrue(sql.contains("FOREIGN KEY (person_id) REFERENCES person(id) ON DELETE CASCADE"));
    }

    @Test
    void arrayOfObjects_childTableWithColumns() {
        JsonObject schema = json("""
            {
              "type": "object",
              "properties": {
                "id": {"type": "string"},
                "orders": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "properties": {
                      "orderId": {"type": "string"},
                      "amount":  {"type": "number"}
                    }
                  }
                }
              }
            }
            """);

        String sql = generator.generateCreateTable("customer", schema);

        assertTrue(sql.contains("CREATE TABLE customer_orders ("));
        assertTrue(sql.contains("customer_id NVARCHAR(255) NOT NULL"));
        assertTrue(sql.contains("position INTEGER NOT NULL"));
        assertTrue(sql.contains("orderId NVARCHAR(255)"));
        assertTrue(sql.contains("amount FLOAT"));
        assertTrue(sql.contains("PRIMARY KEY (customer_id, position)"));
        assertTrue(sql.contains("FOREIGN KEY (customer_id) REFERENCES customer(id) ON DELETE CASCADE"));
    }

    @Test
    void nestedArray_compositeKeyAndFk() {
        JsonObject schema = json("""
            {
              "type": "object",
              "properties": {
                "id": {"type": "string"},
                "orders": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "properties": {
                      "orderId": {"type": "string"},
                      "items": {
                        "type": "array",
                        "items": {
                          "type": "object",
                          "properties": {
                            "productId": {"type": "string"},
                            "quantity":  {"type": "integer"}
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
            """);

        String sql = generator.generateCreateTable("customer", schema);

        // Root
        assertTrue(sql.contains("CREATE TABLE customer ("));
        assertTrue(sql.contains("PRIMARY KEY (id)"));

        // First-level array
        assertTrue(sql.contains("CREATE TABLE customer_orders ("));
        assertTrue(sql.contains("PRIMARY KEY (customer_id, position)"));
        assertTrue(sql.contains("FOREIGN KEY (customer_id) REFERENCES customer(id) ON DELETE CASCADE"));

        // Second-level array: composite PK carries customer_id + orders_position + position
        assertTrue(sql.contains("CREATE TABLE customer_orders_items ("));
        assertTrue(sql.contains("customer_id NVARCHAR(255) NOT NULL"));
        assertTrue(sql.contains("orders_position INTEGER NOT NULL"));
        // The nested items have their own position
        assertTrue(sql.contains("PRIMARY KEY (customer_id, orders_position, position)"));
        assertTrue(sql.contains(
                "FOREIGN KEY (customer_id, orders_position) REFERENCES customer_orders(customer_id, position) ON DELETE CASCADE"));
        assertTrue(sql.contains("productId NVARCHAR(255)"));
        assertTrue(sql.contains("quantity BIGINT"));
    }

    @Test
    void arrayNestedInsideNestedObject() {
        JsonObject schema = json("""
            {
              "type": "object",
              "properties": {
                "id": {"type": "string"},
                "shipping": {
                  "type": "object",
                  "properties": {
                    "carrier": {"type": "string"},
                    "contacts": {
                      "type": "array",
                      "items": {"type": "string"}
                    }
                  }
                }
              }
            }
            """);

        String sql = generator.generateCreateTable("order", schema);

        // Nested object's scalar field is inlined
        assertTrue(sql.contains("shipping__carrier NVARCHAR(255)"));

        // Array inside nested object becomes its own table
        assertTrue(sql.contains("CREATE TABLE order_shipping_contacts ("));
        assertTrue(sql.contains("FOREIGN KEY (order_id) REFERENCES order(id) ON DELETE CASCADE"));
        assertTrue(sql.contains("value NVARCHAR(255)"));
    }

    @Test
    void threeLevel_nestedArrays() {
        JsonObject schema = json("""
            {
              "type": "object",
              "properties": {
                "id": {"type": "string"},
                "orders": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "properties": {
                      "lines": {
                        "type": "array",
                        "items": {
                          "type": "object",
                          "properties": {
                            "batches": {
                              "type": "array",
                              "items": {"type": "string"}
                            }
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
            """);

        String sql = generator.generateCreateTable("inv", schema);

        assertTrue(sql.contains("CREATE TABLE inv_orders ("));
        assertTrue(sql.contains("PRIMARY KEY (inv_id, position)"));

        assertTrue(sql.contains("CREATE TABLE inv_orders_lines ("));
        assertTrue(sql.contains("PRIMARY KEY (inv_id, orders_position, position)"));

        assertTrue(sql.contains("CREATE TABLE inv_orders_lines_batches ("));
        assertTrue(sql.contains("PRIMARY KEY (inv_id, orders_position, lines_position, position)"));
        assertTrue(sql.contains(
                "FOREIGN KEY (inv_id, orders_position, lines_position) REFERENCES inv_orders_lines(inv_id, orders_position, position)"));
    }
}
