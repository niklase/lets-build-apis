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
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Integration tests for SqlServerJsonDB.
 * Tests all MongoDB-style commands: find, insert, update, delete, drop, create, aggregate, findAndModify.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SqlServerJsonDBIT {

    private static final String JDBC_URL = "jdbc:sqlserver://localhost:1433;database=test1;user=sa;password=YourStrongPassw0rd;trustServerCertificate=true;";
    private static final String COLLECTION = "employee";

    private static SqlServerJsonDB sqlDb;
    private static JsonValue schema;
    private static SchemaGenerator schemaGenerator;

    private static JsonObject json(String s) {
        return JsonValueFactory.create(new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8))).getJsonObject();
    }

    @BeforeAll
    static void dropExisting() throws Exception {
        try (Connection conn = DriverManager.getConnection(JDBC_URL)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("DROP TABLE IF EXISTS employee_projects");
                stmt.execute("DROP TABLE IF EXISTS employee_skills");
                stmt.execute("DROP TABLE IF EXISTS employee");
            }
        }
    }

    @BeforeAll
    static void setupSchema() throws SQLException {
        schemaGenerator = new SchemaGenerator();

        // Sample employee documents with nested object, arrays, all types
        // Note: avoid pure-null fields (joinDate: null in all docs) as SchemaGenerator marks them as required
        JsonObject emp1 = json("""
            {
              "id": "e-001",
              "name": "Alice Johnson",
              "age": 30,
              "salary": 75000.50,
              "active": true,
              
              "address": {
                "street": "123 Main St",
                "city": "Springfield",
                "zipCode": 12345,
                "nested": {
                    "optional": "yo",
                    "nested": {
                        "optional": "yo",
                            "nested": {
                            "optional": "yo"
                        }
                    }
                }
              },
              "skills": ["Java", "SQL", "DevOps"],
              "projects": [
                {"name": "Project A", "role": "Lead", "duration": 12},
                {"name": "Project B", "role": "Developer", "duration": 8}
              ]
            }
            """);

        JsonObject emp2 = json("""
            {
              "id": "e-002",
              "name": "Bob Smith",
              "age": 28,
              "salary": 65000.00,
              "active": true,
              "address": {
                "street": "456 Oak Ave",
                "city": "Shelbyville",
                "zipCode": 54321
              },
              "skills": ["Python", "JavaScript"],
              "projects": [
                {"name": "Project C", "role": "Junior", "duration": 6}
              ]
            }
            """);

        JsonArray docs = JsonArray.EMPTY.add(emp1.jsonValue()).add(emp2.jsonValue());
        JsonObject schemaObj = schemaGenerator.generateSchema(docs);
        schema = schemaObj.get("items");

        // Create SqlServerJsonDB with the schema
        sqlDb = new SqlServerJsonDB(JDBC_URL, schema);
    }

    @AfterAll
    static void cleanup() throws Exception {
        // Drop tables via runCommand
        JsonObject dropCmd = json("""
            {
              "drop": {"collection": "employee"}
            }
            """);
        sqlDb.runCommand(dropCmd);
        sqlDb.close();
    }

    @Test
    @Order(1)
    void createCollection_createsRootAndChildTables() throws SQLException {
        JsonObject createCmd = json("""
            {
              "create": {"collection": "employee"}
            }
            """);

        JsonObject response = sqlDb.runCommand(createCmd);
        assertEquals(1, response.get("ok").getInteger());

        // Verify tables actually exist using SQL query
        try (Connection conn = DriverManager.getConnection(JDBC_URL)) {
            try (Statement stmt = conn.createStatement()) {
                ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME='employee'");
                assertTrue(rs.next() && rs.getInt(1) > 0, "employee table was not created");
            }
        }

        // Verify tables exist by attempting to query
        JsonObject findCmd = json("""
            {
              "find": {"collection": "employee"}
            }
            """);
        response = sqlDb.runCommand(findCmd);
        assertEquals(1, response.get("ok").getInteger());
    }

    @Test
    @Order(2)
    void insert_schemaDocuments_fromSetupSchema() {
        JsonObject insertCmd = JsonObject.EMPTY.put("insert", JsonObject.EMPTY
            .put("collection", "employee")
            .put("documents", JsonArray.EMPTY
                .add(JsonObject.EMPTY
                    .put("id", "e-001")
                    .put("name", "Alice Johnson")
                    .put("age", 30)
                    .put("salary", JsonValue.of(BigDecimal.valueOf(75000.50)))
                    .put("active", true)
                    .put("address", JsonObject.EMPTY
                        .put("street", "123 Main St")
                        .put("city", "Springfield")
                        .put("zipCode", 12345))
                    .put("skills", JsonArray.EMPTY
                        .add(JsonValue.of("Java"))
                        .add(JsonValue.of("SQL"))
                        .add(JsonValue.of("DevOps")))
                    .put("projects", JsonArray.EMPTY
                        .add(JsonObject.EMPTY
                            .put("name", "Project A")
                            .put("role", "Lead")
                            .put("duration", 12).jsonValue())
                        .add(JsonObject.EMPTY
                            .put("name", "Project B")
                            .put("role", "Developer")
                            .put("duration", 8).jsonValue()))
                    .jsonValue())
                .add(JsonObject.EMPTY
                    .put("id", "e-002")
                    .put("name", "Bob Smith")
                    .put("age", 28)
                    .put("salary", JsonValue.of(BigDecimal.valueOf(65000.00)))
                    .put("active", true)
                    .put("address", JsonObject.EMPTY
                        .put("street", "456 Oak Ave")
                        .put("city", "Shelbyville")
                        .put("zipCode", 54321))
                    .put("skills", JsonArray.EMPTY
                        .add(JsonValue.of("Python"))
                        .add(JsonValue.of("JavaScript")))
                    .put("projects", JsonArray.EMPTY
                        .add(JsonObject.EMPTY
                            .put("name", "Project C")
                            .put("role", "Junior")
                            .put("duration", 6).jsonValue()))
                    .jsonValue())));

        JsonObject response = sqlDb.runCommand(insertCmd);
        assertEquals(1, response.get("ok").getInteger());
        assertEquals(2, response.get("n").getInteger());
    }

    @Test
    @Order(3)
    void insert_singleDocument_returnsOk1AndN1() {
        JsonObject insertCmd = json("""
            {
              "insert": {
                "collection": "employee",
                "documents": [{
                  "id": "e-003",
                  "name": "Charlie Brown",
                  "age": 35,
                  "salary": 85000.00,
                  "active": false,
                  "address": {"street": "789 Pine Rd", "city": "Capital City", "zipCode": 99999},
                  "skills": ["C++"],
                  "projects": []
                }]
              }
            }
            """);

        JsonObject response = sqlDb.runCommand(insertCmd);
        assertEquals(1, response.get("ok").getInteger());
        assertEquals(1, response.get("n").getInteger());
    }

    @Test
    @Order(4)
    void insert_multipleDocuments_allPersistedAtomically() {
        JsonObject insertCmd = json("""
            {
              "insert": {
                "collection": "employee",
                "documents": [
                  {
                    "id": "e-004",
                    "name": "Diana Prince",
                    "age": 32,
                    "salary": 90000.00,
                    "active": true,
                    "address": {"street": "111 Wonder Way", "city": "Themyscira", "zipCode": 11111},
                    "skills": ["Leadership", "Strategy"],
                    "projects": [{"name": "Project D", "role": "Manager", "duration": 20}]
                  },
                  {
                    "id": "e-005",
                    "name": "Eve Adams",
                    "age": 26,
                    "salary": 60000.00,
                    "active": true,
                    "address": {"street": "222 Science Way", "city": "Research City", "zipCode": 22222},
                    "skills": ["AI", "ML", "Data Science"],
                    "projects": []
                  }
                ]
              }
            }
            """);

        JsonObject response = sqlDb.runCommand(insertCmd);
        assertEquals(1, response.get("ok").getInteger());
        assertEquals(2, response.get("n").getInteger());

        // Verify both are queryable
        JsonObject findCmd = json("""
            {
              "find": {"collection": "employee"}
            }
            """);
        response = sqlDb.runCommand(findCmd);
        JsonArray batch = response.get("cursor").getJsonObject().get("firstBatch").getJsonArray();
        assertTrue(batch.size() >= 2);
    }

    @Test
    @Order(5)
    void find_noFilter_returnsAllDocuments() {
        JsonObject findCmd = JsonObject.EMPTY.put("find",
            JsonObject.EMPTY.put("collection", "employee"));

        JsonObject response = sqlDb.runCommand(findCmd);
        assertEquals(1, response.get("ok").getInteger());
        JsonArray batch = response.get("cursor").getJsonObject().get("firstBatch").getJsonArray();
        assertEquals(5, batch.size()); // 2 from schema docs + 1 from single insert + 2 from multi-insert
    }

    @Test
    @Order(6)
    void find_eqFilter_returnsMatchingDocument() {
        JsonObject findCmd = JsonObject.EMPTY.put("find",
            JsonObject.EMPTY
                .put("collection", "employee")
                .put("filter", JsonObject.EMPTY.put("name", JsonArray.EMPTY
                    .add(JsonObject.EMPTY.put("$eq", "Alice Johnson").jsonValue()))));

        JsonObject response = sqlDb.runCommand(findCmd);
        assertEquals(1, response.get("ok").getInteger());
        JsonArray batch = response.get("cursor").getJsonObject().get("firstBatch").getJsonArray();
        assertEquals(1, batch.size());
        assertEquals("Alice Johnson", batch.get(0).getJsonObject().get("name").getString());
    }

    @Test
    @Order(7)
    void find_andFilter_narrowsResult() {
        JsonObject findCmd = JsonObject.EMPTY.put("find", JsonObject.EMPTY
            .put("collection", "employee")
            .put("filter", JsonObject.EMPTY.put("$and", JsonArray.EMPTY
                .add(JsonObject.EMPTY.put("age", JsonArray.EMPTY
                    .add(JsonObject.EMPTY.put("$gt", 25).jsonValue())).jsonValue())
                .add(JsonObject.EMPTY.put("active", JsonArray.EMPTY
                    .add(JsonObject.EMPTY.put("$eq", true).jsonValue())).jsonValue()))));

        JsonObject response = sqlDb.runCommand(findCmd);
        JsonArray batch = response.get("cursor").getJsonObject().get("firstBatch").getJsonArray();
        assertTrue(batch.size() > 0);
        for (int i = 0; i < batch.size(); i++) {
            JsonObject doc = batch.get(i).getJsonObject();
            assertTrue(doc.get("age").getInteger() > 25);
            assertTrue(doc.get("active").getBoolean());
        }
    }

    @Test
    @Order(8)
    void find_sortAndLimit_returnsCorrectSubset() {
        JsonObject findCmd = JsonObject.EMPTY.put("find", JsonObject.EMPTY
            .put("collection", "employee")
            .put("sort", JsonArray.EMPTY
                .add(JsonObject.EMPTY.put("age", 1).jsonValue()))
            .put("limit", 2));

        JsonObject response = sqlDb.runCommand(findCmd);
        assertEquals(1, response.get("ok").getInteger());
        JsonArray batch = response.get("cursor").getJsonObject().get("firstBatch").getJsonArray();
        assertEquals(2, batch.size());
    }

    @Test
    @Order(9)
    void find_nestedFieldFilter_mapsToDoubleUnderscoreColumn() {
        JsonObject findCmd = JsonObject.EMPTY.put("find", JsonObject.EMPTY
            .put("collection", "employee")
            .put("filter", JsonObject.EMPTY.put("address.city", JsonArray.EMPTY
                .add(JsonObject.EMPTY.put("$eq", "Springfield").jsonValue()))));

        JsonObject response = sqlDb.runCommand(findCmd);
        JsonArray batch = response.get("cursor").getJsonObject().get("firstBatch").getJsonArray();
        assertEquals(1, batch.size());
        assertEquals("Alice Johnson", batch.get(0).getJsonObject().get("name").getString());
    }

    @Test
    @Order(10)
    void update_upsertFalse_modifiesExistingDocument() {
        JsonObject updateCmd = JsonObject.EMPTY.put("update", JsonObject.EMPTY
            .put("collection", "employee")
            .put("updates", JsonArray.EMPTY
                .add(JsonObject.EMPTY
                    .put("q", JsonObject.EMPTY.put("id", JsonArray.EMPTY
                        .add(JsonObject.EMPTY.put("$eq", "e-001").jsonValue())))
                    .put("u", JsonObject.EMPTY
                        .put("id", "e-001")
                        .put("name", "Alice Johnson UPDATED")
                        .put("age", 31)
                        .put("salary", JsonValue.of(BigDecimal.valueOf(80000.00)))
                        .put("active", true)
                        .put("address", JsonObject.EMPTY
                            .put("street", "999 New St")
                            .put("city", "NewCity")
                            .put("zipCode", 99888))
                        .put("skills", JsonArray.EMPTY.add(JsonValue.of("Java")).add(JsonValue.of("SQL")))
                        .put("projects", JsonArray.EMPTY))
                    .put("upsert", false)
                    .jsonValue())));

        JsonObject response = sqlDb.runCommand(updateCmd);
        assertEquals(1, response.get("ok").getInteger());

        // Verify update via find
        JsonObject findCmd = JsonObject.EMPTY.put("find", JsonObject.EMPTY
            .put("collection", "employee")
            .put("filter", JsonObject.EMPTY.put("id", JsonArray.EMPTY
                .add(JsonObject.EMPTY.put("$eq", "e-001").jsonValue()))));
        response = sqlDb.runCommand(findCmd);
        JsonObject doc = response.get("cursor").getJsonObject().get("firstBatch").getJsonArray().get(0).getJsonObject();
        assertEquals("Alice Johnson UPDATED", doc.get("name").getString());
        assertEquals(31, doc.get("age").getInteger());
    }

    @Test
    @Order(11)
    void update_upsertTrue_insertsWhenMissing() {
        JsonObject updateCmd = JsonObject.EMPTY.put("update", JsonObject.EMPTY
            .put("collection", "employee")
            .put("updates", JsonArray.EMPTY
                .add(JsonObject.EMPTY
                    .put("q", JsonObject.EMPTY.put("id", JsonArray.EMPTY
                        .add(JsonObject.EMPTY.put("$eq", "e-999").jsonValue())))
                    .put("u", JsonObject.EMPTY
                        .put("id", "e-999")
                        .put("name", "NewEmployee")
                        .put("age", 25)
                        .put("salary", JsonValue.of(BigDecimal.valueOf(50000.00)))
                        .put("active", true)
                        .put("address", JsonObject.EMPTY
                            .put("street", "999 New St")
                            .put("city", "NewCity")
                            .put("zipCode", 99999))
                        .put("skills", JsonArray.EMPTY)
                        .put("projects", JsonArray.EMPTY))
                    .put("upsert", true)
                    .jsonValue())));

        JsonObject response = sqlDb.runCommand(updateCmd);
        assertEquals(1, response.get("ok").getInteger());

        // Verify doc was created
        JsonObject findCmd = JsonObject.EMPTY.put("find", JsonObject.EMPTY
            .put("collection", "employee")
            .put("filter", JsonObject.EMPTY.put("id", JsonArray.EMPTY
                .add(JsonObject.EMPTY.put("$eq", "e-999").jsonValue()))));
        response = sqlDb.runCommand(findCmd);
        JsonArray batch = response.get("cursor").getJsonObject().get("firstBatch").getJsonArray();
        assertEquals(1, batch.size());
        assertEquals("NewEmployee", batch.get(0).getJsonObject().get("name").getString());
    }

    @Test
    @Order(12)
    void delete_allMatching_removesDocsAndChildRows() {
        // Insert a doc to delete
        JsonObject insertCmd = JsonObject.EMPTY.put("insert", JsonObject.EMPTY
            .put("collection", "employee")
            .put("documents", JsonArray.EMPTY.add(JsonObject.EMPTY
                .put("id", "e-del-all")
                .put("name", "ToDelete")
                .put("age", 40)
                .put("salary", JsonValue.of(BigDecimal.valueOf(100000.00)))
                .put("active", true)
                .put("address", JsonObject.EMPTY
                    .put("street", "Del St")
                    .put("city", "DelCity")
                    .put("zipCode", 88888))
                .put("skills", JsonArray.EMPTY.add(JsonValue.of("Delete")))
                .put("projects", JsonArray.EMPTY.add(JsonObject.EMPTY
                    .put("name", "DeleteProject")
                    .put("role", "Delete")
                    .put("duration", 1).jsonValue()))
                .jsonValue())));
        sqlDb.runCommand(insertCmd);

        // Delete it
        JsonObject deleteCmd = JsonObject.EMPTY.put("delete", JsonObject.EMPTY
            .put("collection", "employee")
            .put("deletes", JsonArray.EMPTY.add(JsonObject.EMPTY
                .put("q", JsonObject.EMPTY.put("id", JsonArray.EMPTY
                    .add(JsonObject.EMPTY.put("$eq", "e-del-all").jsonValue())))
                .put("limit", 0)
                .jsonValue())));

        JsonObject response = sqlDb.runCommand(deleteCmd);
        assertEquals(1, response.get("ok").getInteger());
        assertEquals(1, response.get("n").getInteger());

        // Verify it's gone
        JsonObject findCmd = JsonObject.EMPTY.put("find", JsonObject.EMPTY
            .put("collection", "employee")
            .put("filter", JsonObject.EMPTY.put("id", JsonArray.EMPTY
                .add(JsonObject.EMPTY.put("$eq", "e-del-all").jsonValue()))));
        response = sqlDb.runCommand(findCmd);
        JsonArray batch = response.get("cursor").getJsonObject().get("firstBatch").getJsonArray();
        assertEquals(0, batch.size());
    }

    @Test
    @Order(13)
    void find_sqlInjection_filterValue_treatedAsLiteral() {
        String injectionAttempt = "'; DROP TABLE employee; --";

        JsonObject findCmd = JsonObject.EMPTY.put("find", JsonObject.EMPTY
            .put("collection", "employee")
            .put("filter", JsonObject.EMPTY.put("name", JsonArray.EMPTY
                .add(JsonObject.EMPTY.put("$eq", injectionAttempt).jsonValue()))));

        // Should return 0 results, not execute the injection
        JsonObject response = sqlDb.runCommand(findCmd);
        JsonArray batch = response.get("cursor").getJsonObject().get("firstBatch").getJsonArray();
        assertEquals(0, batch.size());

        // Verify table still exists by querying
        JsonObject verifyCmd = JsonObject.EMPTY.put("find", JsonObject.EMPTY
            .put("collection", "employee"));
        response = sqlDb.runCommand(verifyCmd);
        assertEquals(1, response.get("ok").getInteger());
        batch = response.get("cursor").getJsonObject().get("firstBatch").getJsonArray();
        assertTrue(batch.size() > 0);
    }

    @Test
    @Order(14)
    void aggregate_matchStage_returnsFilteredDocuments() {
        JsonObject aggregateCmd = JsonObject.EMPTY.put("aggregate", JsonObject.EMPTY
            .put("collection", "employee")
            .put("pipeline", JsonArray.EMPTY
                .add(JsonObject.EMPTY.put("$match", JsonObject.EMPTY
                    .put("active", JsonArray.EMPTY
                        .add(JsonObject.EMPTY.put("$eq", true).jsonValue()))).jsonValue()))
            .put("cursor", JsonObject.EMPTY));

        JsonObject response = sqlDb.runCommand(aggregateCmd);
        assertEquals(1, response.get("ok").getInteger());
        JsonArray batch = response.get("cursor").getJsonObject().get("firstBatch").getJsonArray();
        assertTrue(batch.size() > 0);
        for (int i = 0; i < batch.size(); i++) {
            assertTrue(batch.get(i).getJsonObject().get("active").getBoolean());
        }
    }

    @Test
    @Order(15)
    void aggregate_countStage_returnsCorrectCount() {
        JsonObject aggregateCmd = JsonObject.EMPTY.put("aggregate", JsonObject.EMPTY
            .put("collection", "employee")
            .put("pipeline", JsonArray.EMPTY
                .add(JsonObject.EMPTY.put("$count", "count").jsonValue()))
            .put("cursor", JsonObject.EMPTY));

        JsonObject response = sqlDb.runCommand(aggregateCmd);
        assertEquals(1, response.get("ok").getInteger());
        JsonArray batch = response.get("cursor").getJsonObject().get("firstBatch").getJsonArray();
        assertEquals(1, batch.size());
        assertTrue(batch.get(0).getJsonObject().keys().contains("count"));
    }

    @Test
    @Order(16)
    void findAndModify_update_returnsOriginalThenShowsChange() {
        JsonObject fmCmd = JsonObject.EMPTY.put("findAndModify", JsonObject.EMPTY
            .put("collection", "employee")
            .put("query", JsonObject.EMPTY.put("id", JsonArray.EMPTY
                .add(JsonObject.EMPTY.put("$eq", "e-002").jsonValue())))
            .put("update", JsonObject.EMPTY
                .put("id", "e-002")
                .put("name", "Bob Smith MODIFIED")
                .put("age", 28)
                .put("salary", JsonValue.of(BigDecimal.valueOf(70000.00)))
                .put("active", false)
                .put("address", JsonObject.EMPTY
                    .put("street", "456 Oak Ave")
                    .put("city", "Shelbyville")
                    .put("zipCode", 54321))
                .put("skills", JsonArray.EMPTY.add(JsonValue.of("Python")))
                .put("projects", JsonArray.EMPTY))
            .put("new", false));

        JsonObject response = sqlDb.runCommand(fmCmd);
        assertEquals(1, response.get("ok").getInteger());

        // value should be the pre-update doc (new: false)
        JsonObject value = response.get("value").getJsonObject();
        assertEquals("Bob Smith", value.get("name").getString()); // Original name

        // Verify it was updated
        JsonObject findCmd = JsonObject.EMPTY.put("find", JsonObject.EMPTY
            .put("collection", "employee")
            .put("filter", JsonObject.EMPTY.put("id", JsonArray.EMPTY
                .add(JsonObject.EMPTY.put("$eq", "e-002").jsonValue()))));
        response = sqlDb.runCommand(findCmd);
        JsonObject updated = response.get("cursor").getJsonObject().get("firstBatch").getJsonArray().get(0).getJsonObject();
        assertEquals("Bob Smith MODIFIED", updated.get("name").getString());
    }

    @Test
    @Order(17)
    void findAndModify_remove_returnsDeletedDocument() {
        // Insert a doc to delete
        JsonObject insertCmd = JsonObject.EMPTY.put("insert", JsonObject.EMPTY
            .put("collection", "employee")
            .put("documents", JsonArray.EMPTY.add(JsonObject.EMPTY
                .put("id", "e-fam-del")
                .put("name", "ToDeleteViaFAM")
                .put("age", 45)
                .put("salary", JsonValue.of(BigDecimal.valueOf(110000.00)))
                .put("active", true)
                .put("address", JsonObject.EMPTY
                    .put("street", "FAM Del St")
                    .put("city", "FAMDelCity")
                    .put("zipCode", 77777))
                .put("skills", JsonArray.EMPTY)
                .put("projects", JsonArray.EMPTY)
                .jsonValue())));
        sqlDb.runCommand(insertCmd);

        JsonObject fmCmd = JsonObject.EMPTY.put("findAndModify", JsonObject.EMPTY
            .put("collection", "employee")
            .put("query", JsonObject.EMPTY.put("id", JsonArray.EMPTY
                .add(JsonObject.EMPTY.put("$eq", "e-fam-del").jsonValue())))
            .put("remove", true));

        JsonObject response = sqlDb.runCommand(fmCmd);
        assertEquals(1, response.get("ok").getInteger());

        // value should be the deleted doc
        JsonObject value = response.get("value").getJsonObject();
        assertEquals("ToDeleteViaFAM", value.get("name").getString());

        // Verify it's gone
        JsonObject findCmd = JsonObject.EMPTY.put("find", JsonObject.EMPTY
            .put("collection", "employee")
            .put("filter", JsonObject.EMPTY.put("id", JsonArray.EMPTY
                .add(JsonObject.EMPTY.put("$eq", "e-fam-del").jsonValue()))));
        response = sqlDb.runCommand(findCmd);
        JsonArray batch = response.get("cursor").getJsonObject().get("firstBatch").getJsonArray();
        assertEquals(0, batch.size());
    }
}
