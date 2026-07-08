package com.zuunr.sqlschema;

import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import com.zuunr.json.JsonValueFactory;
import com.zuunr.json.schema.generation.SchemaGenerator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SqlRoundTripIT {

    private static final String JDBC_URL = "jdbc:sqlserver://localhost:1433;database=test1;user=sa;password=YourStrongPassw0rd;trustServerCertificate=true;";
    private static final String TABLE_NAME = "product";
    private static final String VARIANTS_TABLE = TABLE_NAME + "_variants";
    private static final String TAGS_TABLE = TABLE_NAME + "_tags";

    private static Connection conn;
    private static JsonObject schema;

    private final SchemaGenerator schemaGenerator = new SchemaGenerator();
    private final FlywayMigrationGenerator migrationGenerator = new FlywayMigrationGenerator();
    private final SqlUpsertGenerator upsertGenerator = new SqlUpsertGenerator();
    private final SqlQueryGenerator queryGenerator = new SqlQueryGenerator();

    private static JsonObject json(String s) {
        return JsonValueFactory.create(new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8))).getJsonObject();
    }

    private static JsonArray jsonArray(String s) {
        return JsonValueFactory.create(new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8))).getJsonArray();
    }

    @BeforeAll
    static void connect() throws SQLException {
        conn = DriverManager.getConnection(JDBC_URL);
    }

    @AfterAll
    static void cleanup() throws SQLException {
        if (conn != null) {
            try (Statement stmt = conn.createStatement()) {
                try { stmt.execute("DROP TABLE IF EXISTS " + VARIANTS_TABLE); } catch (Exception e) {}
                try { stmt.execute("DROP TABLE IF EXISTS " + TAGS_TABLE); } catch (Exception e) {}
                try { stmt.execute("DROP TABLE IF EXISTS " + TABLE_NAME); } catch (Exception e) {}
            }
            conn.close();
        }
    }

    @Test
    @Order(1)
    void schemaGeneratedFromDocuments() {
        JsonObject doc1 = json("""
            {
              "id": "p-001",
              "name": "Widget Pro",
              "price": 1.5,
              "stock": 100,
              "active": true,
              "alwaysNull": null,
              "specs": {
                "brand": "Acme",
                "weight": 2.5,
                "certified": false
              },
              "tags": ["hardware", "tools"],
              "variants": [
                {"sku": "WP-RED", "color": "red", "qty": 10},
                {"sku": "WP-BLUE", "color": "blue", "qty": 5}
              ]
            }
            """);

        JsonObject doc2 = json("""
            {
              "id": "p-002",
              "name": "Gadget Plus",
              "price": 2.5,
              "stock": 50,
              "active": false,
              "description": "An advanced gadget",
              "specs": {
                "brand": "Beta",
                "weight": 1.5,
                "certified": true
              },
              "tags": ["software"],
              "variants": [
                {"sku": "GP-GREEN", "color": "green", "qty": 20}
              ]
            }
            """);

        JsonArray docs = JsonArray.EMPTY.add(doc1.jsonValue()).add(doc2.jsonValue());

        JsonObject arraySchema = schemaGenerator.generateSchema(docs);
        schema = arraySchema.get("items").getJsonObject();

        assertNotNull(schema);
        JsonObject properties = schema.get("properties").getJsonObject();
        assertTrue(properties.keys().contains("id"));
        assertTrue(properties.keys().contains("name"));
        assertTrue(properties.keys().contains("price"));
        assertTrue(properties.keys().contains("stock"));
        assertTrue(properties.keys().contains("active"));
        assertTrue(properties.keys().contains("alwaysNull"));
        assertTrue(properties.keys().contains("specs"));
        assertTrue(properties.keys().contains("tags"));
        assertTrue(properties.keys().contains("variants"));

        // Verify types
        assertEquals("string", properties.get("name").getJsonObject().get("type").getString());
        assertEquals("object", properties.get("specs").getJsonObject().get("type").getString());
        assertEquals("array", properties.get("tags").getJsonObject().get("type").getString());
        assertEquals("array", properties.get("variants").getJsonObject().get("type").getString());
    }

    @Test
    @Order(2)
    void tablesCreatedFromSchema() throws SQLException {
        String ddl = migrationGenerator.generateCreateTable(TABLE_NAME, schema);

        // Split on ;\n and execute each statement
        String[] statements = ddl.split(";\n");
        try (Statement stmt = conn.createStatement()) {
            for (String s : statements) {
                if (!s.trim().isEmpty()) {
                    stmt.execute(s.trim() + ";");
                }
            }
        }

        // Verify tables exist by querying them
        try (Statement stmt = conn.createStatement()) {
            var rs = stmt.executeQuery("SELECT COUNT(*) FROM " + TABLE_NAME);
            assertTrue(rs.next());
            rs = stmt.executeQuery("SELECT COUNT(*) FROM " + TAGS_TABLE);
            assertTrue(rs.next());
            rs = stmt.executeQuery("SELECT COUNT(*) FROM " + VARIANTS_TABLE);
            assertTrue(rs.next());
        }
    }

    @Test
    @Order(3)
    void documentsInsertedAndQueriedBack() throws SQLException {
        JsonObject doc1 = json("""
            {
              "id": "p-001",
              "name": "Widget Pro",
              "price": 1.5,
              "stock": 100,
              "active": true,
              "alwaysNull": null,
              "specs": {
                "brand": "Acme",
                "weight": 2.5,
                "certified": false
              },
              "tags": ["hardware", "tools"],
              "variants": [
                {"sku": "WP-RED", "color": "red", "qty": 10},
                {"sku": "WP-BLUE", "color": "blue", "qty": 5}
              ]
            }
            """);

        JsonObject doc2 = json("""
            {
              "id": "p-002",
              "name": "Gadget Plus",
              "price": 2.5,
              "stock": 50,
              "active": false,
              "description": "An advanced gadget",
              "specs": {
                "brand": "Beta",
                "weight": 1.5,
                "certified": true
              },
              "tags": ["software"],
              "variants": [
                {"sku": "GP-GREEN", "color": "green", "qty": 20}
              ]
            }
            """);

        JsonArray docs = JsonArray.EMPTY.add(doc1.jsonValue()).add(doc2.jsonValue());

        // Insert documents
        JsonObject insertCmd = JsonObject.EMPTY.put("insert", JsonObject.EMPTY
            .put("collection", TABLE_NAME)
            .put("documents", docs.jsonValue()));

        String upsertSql = upsertGenerator.generateUpsert(insertCmd);
        String[] statements = upsertSql.split(";\n");
        try (Statement stmt = conn.createStatement()) {
            for (String s : statements) {
                if (!s.trim().isEmpty()) {
                    stmt.execute(s.trim() + ";");
                }
            }
        }

        // Query back all documents
        JsonObject findArgs = JsonObject.EMPTY.put("collection", TABLE_NAME);
        JsonArray results = queryGenerator.queryDocuments(conn, findArgs, schema);

        assertEquals(2, results.size());

        // Find both documents in results (order may vary)
        boolean found1 = false, found2 = false;
        for (int i = 0; i < results.size(); i++) {
            JsonObject result = results.get(i).getJsonObject();
            String id = result.get("id").getString();
            if ("p-001".equals(id)) {
                found1 = true;
                assertEquals("Widget Pro", result.get("name").getString());
                assertEquals(100, (long) result.get("stock").getInteger());
                assertTrue(result.get("active").getBoolean());
            } else if ("p-002".equals(id)) {
                found2 = true;
                assertEquals("Gadget Plus", result.get("name").getString());
                assertEquals(50, (long) result.get("stock").getInteger());
                assertFalse(result.get("active").getBoolean());
            }
        }
        assertTrue(found1, "Document p-001 not found in results");
        assertTrue(found2, "Document p-002 not found in results");
    }

    @Test
    @Order(4)
    void filterWithPreparedStatement() throws SQLException {
        // Query with filter - Widget Pro should match
        JsonObject findArgs = JsonObject.EMPTY
            .put("collection", TABLE_NAME)
            .put("filter", JsonObject.EMPTY.put("name", JsonArray.EMPTY
                .add(JsonObject.EMPTY.put("$eq", "Widget Pro").jsonValue())));

        JsonArray results = queryGenerator.queryDocuments(conn, findArgs, schema);
        assertEquals(1, results.size());
        assertEquals("Widget Pro", results.get(0).getJsonObject().get("name").getString());

        // Query with non-existent value should return empty
        JsonObject findArgsEmpty = JsonObject.EMPTY
            .put("collection", TABLE_NAME)
            .put("filter", JsonObject.EMPTY.put("name", JsonArray.EMPTY
                .add(JsonObject.EMPTY.put("$eq", "Nonexistent Product").jsonValue())));

        JsonArray emptyResults = queryGenerator.queryDocuments(conn, findArgsEmpty, schema);
        assertEquals(0, emptyResults.size());
    }

    @Test
    @Order(5)
    void sqlInjectionInFilter() throws SQLException {
        // Attempt SQL injection in filter - should be treated as literal string
        String injectionAttempt = "'; DROP TABLE " + TABLE_NAME + "; --";
        JsonObject findArgs = JsonObject.EMPTY
            .put("collection", TABLE_NAME)
            .put("filter", JsonObject.EMPTY.put("name", JsonArray.EMPTY
                .add(JsonObject.EMPTY.put("$eq", injectionAttempt).jsonValue())));

        // This should return 0 results (no match for the injection string)
        JsonArray results = queryGenerator.queryDocuments(conn, findArgs, schema);
        assertEquals(0, results.size());

        // Verify the table still exists
        try (Statement stmt = conn.createStatement()) {
            var rs = stmt.executeQuery("SELECT COUNT(*) FROM " + TABLE_NAME);
            assertTrue(rs.next());
            assertEquals(2, rs.getInt(1)); // Still has 2 rows
        }
    }

    @Test
    @Order(6)
    void upsertUpdatesDocument() throws SQLException {
        // Modify doc1 and re-insert
        JsonObject updatedDoc1 = json("""
            {
              "id": "p-001",
              "name": "Widget Pro - Updated",
              "price": 3.5,
              "stock": 150,
              "active": false,
              "alwaysNull": null,
              "specs": {
                "brand": "Acme Corp",
                "weight": 3.0,
                "certified": true
              },
              "tags": ["hardware", "tools", "premium"],
              "variants": [
                {"sku": "WP-RED", "color": "red", "qty": 20}
              ]
            }
            """);

        JsonArray docs = JsonArray.EMPTY.add(updatedDoc1.jsonValue());
        JsonObject insertCmd = JsonObject.EMPTY.put("insert", JsonObject.EMPTY
            .put("collection", TABLE_NAME)
            .put("documents", docs.jsonValue()));

        String upsertSql = upsertGenerator.generateUpsert(insertCmd);
        String[] statements = upsertSql.split(";\n");
        try (Statement stmt = conn.createStatement()) {
            for (String s : statements) {
                if (!s.trim().isEmpty()) {
                    stmt.execute(s.trim() + ";");
                }
            }
        }

        // Query back the updated document
        JsonObject findArgs = JsonObject.EMPTY
            .put("collection", TABLE_NAME)
            .put("filter", JsonObject.EMPTY.put("id", JsonArray.EMPTY
                .add(JsonObject.EMPTY.put("$eq", "p-001").jsonValue())));

        JsonArray results = queryGenerator.queryDocuments(conn, findArgs, schema);
        assertEquals(1, results.size());

        JsonObject result = results.get(0).getJsonObject();
        assertEquals("Widget Pro - Updated", result.get("name").getString());
        assertEquals(150, (long) result.get("stock").getInteger());
        assertFalse(result.get("active").getBoolean());
        assertTrue(result.get("alwaysNull").isNull());
        assertEquals("Acme Corp", result.get("specs").getJsonObject().get("brand").getString());
    }
}
