package com.zuunr.sqlschema;

import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * SQL Server equivalent of {@link com.zuunr.mongodb.MongoJsonDB}.
 * Accepts MongoDB-style commands and returns identical response JSON shapes.
 *
 * Thread-safe: each {@code runCommand} call acquires its own {@link Connection},
 * executes the command, and closes the connection. No connection is shared between calls.
 *
 * Precondition: SQL Server tables are created from the JSON Schema (via {@link FlywayMigrationGenerator}).
 *
 * Commands supported:
 * - {@code find}: SELECT with WHERE, ORDER BY, LIMIT, OFFSET
 * - {@code insert}: MERGE (upsert by id) with optional transaction for multi-document
 * - {@code update}: UPDATE WHERE for non-upsert, MERGE for upsert
 * - {@code delete}: DELETE FROM WHERE with optional transaction for multi-delete
 * - {@code drop}: DROP TABLE IF EXISTS for root and child tables
 * - {@code create}: CREATE TABLE IF NOT EXISTS
 * - {@code aggregate}: $match (equivalent to find), $count
 * - {@code findAndModify}: SELECT + UPDATE/DELETE in a transaction
 */
public class SqlServerJsonDB implements AutoCloseable {

    private final DataSource dataSource;
    private final String jdbcUrl;
    private final JsonValue schema;

    private final FlywayMigrationGenerator migrationGenerator = new FlywayMigrationGenerator();
    private final SqlUpsertGenerator upsertGenerator = new SqlUpsertGenerator();
    private final SqlQueryGenerator queryGenerator = new SqlQueryGenerator();

    /**
     * Construct with JDBC URL. Each {@code runCommand} will create a new {@link Connection}
     * via {@link DriverManager}.
     */
    public SqlServerJsonDB(String jdbcUrl, JsonValue schema) {
        this.jdbcUrl = jdbcUrl;
        this.dataSource = null;
        this.schema = schema;
    }

    /**
     * Construct with {@link DataSource}. Each {@code runCommand} will borrow a {@link Connection}
     * from the pool.
     */
    public SqlServerJsonDB(DataSource dataSource, JsonValue schema) {
        this.dataSource = dataSource;
        this.jdbcUrl = null;
        this.schema = schema;
    }

    /**
     * Execute a MongoDB-style command. Returns a JSON response with {@code ok: 1} on success,
     * {@code ok: 0, errmsg: "..."} on error.
     *
     * @param command a JsonObject with exactly one key (the command name)
     * @return response JsonObject
     */
    public JsonObject runCommand(JsonObject command) {
        try (Connection conn = acquireConnection()) {
            return dispatch(conn, command);
        } catch (SQLException e) {
            return JsonObject.EMPTY.put("ok", JsonValue.of(0)).put("errmsg", JsonValue.of(e.getMessage()));
        }
    }

    private Connection acquireConnection() throws SQLException {
        if (dataSource != null) {
            return dataSource.getConnection();
        } else {
            // Extract credentials from JDBC URL or use default
            // Format: jdbc:sqlserver://localhost:1433;database=test1;user=sa;password=pwd;trustServerCertificate=true;
            return DriverManager.getConnection(jdbcUrl);
        }
    }

    private JsonObject dispatch(Connection conn, JsonObject command) throws SQLException {
        String commandName = command.keys().get(0).getString();
        JsonObject commandArgs = command.values().get(0).getJsonObject();

        switch (commandName) {
            case "find":
                return handleFind(conn, commandArgs);
            case "insert":
                return handleInsert(conn, commandArgs);
            case "update":
                return handleUpdate(conn, commandArgs);
            case "delete":
                return handleDelete(conn, commandArgs);
            case "drop":
                return handleDrop(conn, commandArgs);
            case "create":
                return handleCreate(conn, commandArgs);
            case "aggregate":
                return handleAggregate(conn, commandArgs);
            case "findAndModify":
                return handleFindAndModify(conn, commandArgs);
            default:
                throw new RuntimeException("Unsupported command: " + commandName);
        }
    }

    private JsonObject handleFind(Connection conn, JsonObject findArgs) throws SQLException {
        JsonArray results = queryGenerator.queryDocuments(conn, findArgs, schema.getJsonObject());
        return JsonObject.EMPTY
            .put("ok", JsonValue.of(1))
            .put("cursor", JsonObject.EMPTY
                .put("firstBatch", results.jsonValue()));
    }

    private JsonObject handleInsert(Connection conn, JsonObject insertArgs) throws SQLException {
        JsonArray documents = insertArgs.get("documents", JsonValue.NULL).getJsonArray();
        if (documents == null || documents.isEmpty()) {
            throw new RuntimeException("insert requires documents array");
        }

        boolean autoTx = documents.size() > 1;
        if (autoTx) {
            conn.setAutoCommit(false);
        }

        try {
            String upsertSql = upsertGenerator.generateUpsert(insertArgs);
            executeMultipleStatements(conn, upsertSql);

            if (autoTx) {
                conn.commit();
            }

            return JsonObject.EMPTY
                .put("ok", JsonValue.of(1))
                .put("n", JsonValue.of((long) documents.size()));
        } catch (SQLException e) {
            if (autoTx) {
                conn.rollback();
            }
            throw e;
        } finally {
            if (autoTx) {
                conn.setAutoCommit(true);
            }
        }
    }

    private JsonObject handleUpdate(Connection conn, JsonObject updateArgs) throws SQLException {
        JsonArray updates = updateArgs.get("updates", JsonValue.NULL).getJsonArray();
        if (updates == null || updates.isEmpty()) {
            throw new RuntimeException("update requires updates array");
        }

        String collection = updateArgs.get("collection", JsonValue.NULL).getString();
        if (collection == null) {
            throw new RuntimeException("update requires collection");
        }

        boolean autoTx = updates.size() > 1;
        if (autoTx) {
            conn.setAutoCommit(false);
        }

        try {
            int totalMatched = 0;
            int totalModified = 0;

            for (int i = 0; i < updates.size(); i++) {
                JsonObject update = updates.get(i).getJsonObject();
                JsonObject q = update.get("q", JsonValue.NULL).getJsonObject();
                JsonObject u = update.get("u", JsonValue.NULL).getJsonObject();
                JsonValue upsertVal = update.get("upsert", JsonValue.NULL);
                boolean upsert = upsertVal.isNull() ? false : upsertVal.getBoolean();

                if (upsert) {
                    // Use MERGE for upsert
                    JsonObject insertCmd = JsonObject.EMPTY.put("insert", JsonObject.EMPTY
                        .put("collection", JsonValue.of(collection))
                        .put("documents", JsonArray.EMPTY.add(u).jsonValue())
                        .jsonValue());
                    String upsertSql = upsertGenerator.generateUpsert(insertCmd);
                    executeMultipleStatements(conn, upsertSql);
                    totalModified++;
                } else {
                    // UPDATE WHERE (not upsert)
                    int modified = executeUpdate(conn, collection, q, u);
                    totalModified += modified;
                }
            }

            if (autoTx) {
                conn.commit();
            }

            return JsonObject.EMPTY
                .put("ok", JsonValue.of(1))
                .put("n", JsonValue.of((long) totalMatched))
                .put("nModified", JsonValue.of((long) totalModified));
        } catch (SQLException e) {
            if (autoTx) {
                conn.rollback();
            }
            throw e;
        } finally {
            if (autoTx) {
                conn.setAutoCommit(true);
            }
        }
    }

    private int executeUpdate(Connection conn, String collection, JsonObject filter, JsonObject updateDoc) throws SQLException {
        JsonObject schema = this.schema.getJsonObject();

        // Build UPDATE SET from updateDoc (flatten nested objects)
        List<String> setClauses = new ArrayList<>();
        List<Object> params = new ArrayList<>();

        flattenForUpdate(updateDoc, "", setClauses, params, schema);

        if (setClauses.isEmpty()) {
            return 0; // Nothing to update
        }

        StringBuilder sql = new StringBuilder("UPDATE ").append(collection).append(" SET ");
        for (int i = 0; i < setClauses.size(); i++) {
            if (i > 0) sql.append(", ");
            sql.append(setClauses.get(i));
        }

        // Add WHERE clause
        if (filter != null && !filter.isEmpty()) {
            sql.append(" WHERE ").append(queryGenerator.generatePreparedDelete(collection, filter).sql.substring("DELETE FROM ".length() + collection.length() + " ".length()));
        }

        // Build the full prepared query
        SqlQueryGenerator.PreparedQuery whereQuery = queryGenerator.generatePreparedDelete(collection, filter);
        String wherePart = whereQuery.sql.substring(("DELETE FROM " + collection + " WHERE ").length());

        sql = new StringBuilder("UPDATE ").append(collection).append(" SET ");
        for (int i = 0; i < setClauses.size(); i++) {
            if (i > 0) sql.append(", ");
            sql.append(setClauses.get(i));
        }
        if (filter != null && !filter.isEmpty()) {
            sql.append(" WHERE ").append(wherePart);
        }

        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int paramIndex = 1;
            for (Object param : params) {
                ps.setObject(paramIndex++, param);
            }
            for (Object param : whereQuery.params) {
                ps.setObject(paramIndex++, param);
            }
            return ps.executeUpdate();
        }
    }

    private void flattenForUpdate(JsonObject doc, String prefix, List<String> setClauses, List<Object> params, JsonObject schema) {
        for (int i = 0; i < doc.keys().size(); i++) {
            String key = doc.keys().get(i).getString();
            JsonValue val = doc.values().get(i);
            String col = prefix.isEmpty() ? key : prefix + "__" + key;

            if (val.isJsonObject()) {
                flattenForUpdate(val.getJsonObject(), col, setClauses, params, schema);
            } else if (!val.isJsonArray()) {
                setClauses.add(col + " = ?");
                params.add(toObjectValue(val));
            }
        }
    }

    private Object toObjectValue(JsonValue value) {
        if (value.isNull()) return null;
        if (value.isString()) return value.getString();
        if (value.isInteger()) return value.getInteger();
        if (value.isBigDecimal()) return value.getBigDecimal().doubleValue();
        if (value.isBoolean()) return value.getBoolean();
        return null;
    }

    private JsonObject handleDelete(Connection conn, JsonObject deleteArgs) throws SQLException {
        JsonArray deletes = deleteArgs.get("deletes", JsonValue.NULL).getJsonArray();
        if (deletes == null || deletes.isEmpty()) {
            throw new RuntimeException("delete requires deletes array");
        }

        String collection = deleteArgs.get("collection", JsonValue.NULL).getString();
        if (collection == null) {
            throw new RuntimeException("delete requires collection");
        }

        boolean autoTx = deletes.size() > 1;
        if (autoTx) {
            conn.setAutoCommit(false);
        }

        try {
            int totalDeleted = 0;

            for (int i = 0; i < deletes.size(); i++) {
                JsonObject delete = deletes.get(i).getJsonObject();
                JsonObject q = delete.get("q", JsonValue.NULL).getJsonObject();
                JsonValue limitVal = delete.get("limit", JsonValue.NULL);
                int limit = limitVal.isNull() ? 0 : limitVal.getInteger();

                SqlQueryGenerator.PreparedQuery pq = queryGenerator.generatePreparedDelete(collection, q);
                String sql = pq.sql;
                if (limit == 1) {
                    sql += " LIMIT 1"; // DELETE TOP 1 or LIMIT 1 (SQL Server 2022+)
                }

                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    for (int j = 0; j < pq.params.size(); j++) {
                        ps.setObject(j + 1, pq.params.get(j));
                    }
                    totalDeleted += ps.executeUpdate();
                }
            }

            if (autoTx) {
                conn.commit();
            }

            return JsonObject.EMPTY
                .put("ok", JsonValue.of(1))
                .put("n", JsonValue.of((long) totalDeleted));
        } catch (SQLException e) {
            if (autoTx) {
                conn.rollback();
            }
            throw e;
        } finally {
            if (autoTx) {
                conn.setAutoCommit(true);
            }
        }
    }

    private JsonObject handleDrop(Connection conn, JsonObject dropArgs) throws SQLException {
        String collection = dropArgs.get("collection", JsonValue.NULL).getString();
        if (collection == null) {
            throw new RuntimeException("drop requires collection");
        }

        JsonObject schema = this.schema.getJsonObject();
        JsonObject properties = schema.get("properties", JsonValue.NULL).getJsonObject();

        try (Statement stmt = conn.createStatement()) {
            // Drop child tables first (in reverse order of properties)
            if (properties != null) {
                List<String> childTables = new ArrayList<>();
                for (int i = 0; i < properties.keys().size(); i++) {
                    JsonObject prop = properties.values().get(i).getJsonObject();
                    String type = extractTypeString(prop.get("type", JsonValue.NULL));
                    if ("array".equals(type)) {
                        String fieldName = properties.keys().get(i).getString();
                        childTables.add(collection + "_" + fieldName);
                    }
                }

                // Drop in reverse order
                for (int i = childTables.size() - 1; i >= 0; i--) {
                    String childTable = childTables.get(i);
                    stmt.execute("DROP TABLE IF EXISTS " + childTable);
                }
            }

            // Drop root table
            stmt.execute("DROP TABLE IF EXISTS " + collection);
        }

        return JsonObject.EMPTY.put("ok", JsonValue.of(1));
    }

    private JsonObject handleCreate(Connection conn, JsonObject createArgs) throws SQLException {
        String collection = createArgs.get("collection", JsonValue.NULL).getString();
        if (collection == null) {
            throw new RuntimeException("create requires collection");
        }

        JsonObject schema = this.schema.getJsonObject();
        String ddl = migrationGenerator.generateCreateTableIfNotExists(collection, schema);

        executeMultipleStatements(conn, ddl);

        return JsonObject.EMPTY.put("ok", JsonValue.of(1));
    }

    private JsonObject handleAggregate(Connection conn, JsonObject aggregateArgs) throws SQLException {
        String collection = aggregateArgs.get("collection", JsonValue.NULL).getString();
        if (collection == null) {
            throw new RuntimeException("aggregate requires collection");
        }

        JsonArray pipeline = aggregateArgs.get("pipeline", JsonValue.NULL).getJsonArray();
        if (pipeline == null || pipeline.isEmpty()) {
            throw new RuntimeException("aggregate requires pipeline");
        }

        // Support $match and $count stages
        JsonObject matchStage = null;
        boolean hasCount = false;

        for (int i = 0; i < pipeline.size(); i++) {
            JsonObject stage = pipeline.get(i).getJsonObject();
            if (stage.keys().contains("$match")) {
                matchStage = stage.get("$match").getJsonObject();
            } else if (stage.keys().contains("$count")) {
                hasCount = true;
            } else {
                throw new UnsupportedOperationException("Unsupported pipeline stage: " + stage.keys().get(0).getString());
            }
        }

        JsonArray results;
        if (hasCount) {
            // SELECT COUNT(*)
            String sql = "SELECT COUNT(*) as count FROM " + collection;
            if (matchStage != null) {
                SqlQueryGenerator.PreparedQuery pq = queryGenerator.generatePreparedDelete(collection, matchStage);
                String where = pq.sql.substring(("DELETE FROM " + collection).length());
                sql += where;
            }

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        JsonObject countDoc = JsonObject.EMPTY.put("count", JsonValue.of(rs.getLong(1)));
                        results = JsonArray.EMPTY.add(countDoc);
                    } else {
                        results = JsonArray.EMPTY;
                    }
                }
            }
        } else {
            // $match equivalent to find
            JsonObject findArgs = JsonObject.EMPTY.put("collection", JsonValue.of(collection));
            if (matchStage != null) {
                findArgs = findArgs.put("filter", matchStage.jsonValue());
            }
            results = queryGenerator.queryDocuments(conn, findArgs, schema.getJsonObject());
        }

        return JsonObject.EMPTY
            .put("ok", JsonValue.of(1))
            .put("cursor", JsonObject.EMPTY
                .put("firstBatch", results.jsonValue()));
    }

    private JsonObject handleFindAndModify(Connection conn, JsonObject fmArgs) throws SQLException {
        String collection = fmArgs.get("collection", JsonValue.NULL).getString();
        JsonObject query = fmArgs.get("query", JsonValue.NULL).getJsonObject();
        JsonObject update = fmArgs.get("update", JsonValue.NULL).getJsonObject();
        JsonValue removeVal = fmArgs.get("remove", JsonValue.NULL);
        boolean remove = removeVal.isNull() ? false : removeVal.getBoolean();
        JsonValue newVal_jsonValue = fmArgs.get("new", JsonValue.NULL);
        boolean newVal = newVal_jsonValue.isNull() ? false : newVal_jsonValue.getBoolean();

        conn.setAutoCommit(false);
        try {
            // Find the matching document
            JsonObject findArgs = JsonObject.EMPTY.put("collection", JsonValue.of(collection));
            if (query != null) {
                findArgs = findArgs.put("filter", query.jsonValue());
            }

            JsonArray found = queryGenerator.queryDocuments(conn, findArgs, schema.getJsonObject());
            JsonObject value = found.isEmpty() ? null : found.get(0).getJsonObject();

            if (remove) {
                // Delete the document
                if (value != null && value.keys().contains("id")) {
                    String id = value.get("id").getString();
                    String sql = "DELETE FROM " + collection + " WHERE id = ?";
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setString(1, id);
                        ps.executeUpdate();
                    }
                }
                // Return the original document
            } else if (update != null && value != null) {
                // Update the document
                String id = value.get("id").getString();
                JsonObject updateFilter = JsonObject.EMPTY.put("id", JsonArray.EMPTY.add(JsonObject.EMPTY.put("$eq", JsonValue.of(id))));
                executeUpdate(conn, collection, updateFilter, update);

                if (newVal) {
                    // Query back the updated document
                    JsonObject findArgsAfter = JsonObject.EMPTY
                        .put("collection", JsonValue.of(collection))
                        .put("filter", updateFilter.jsonValue());
                    JsonArray foundAfter = queryGenerator.queryDocuments(conn, findArgsAfter, schema.getJsonObject());
                    if (!foundAfter.isEmpty()) {
                        value = foundAfter.get(0).getJsonObject();
                    }
                }
            }

            conn.commit();

            return JsonObject.EMPTY
                .put("ok", JsonValue.of(1))
                .put("value", value != null ? value.jsonValue() : JsonValue.NULL)
                .put("lastErrorObject", JsonObject.EMPTY
                    .put("n", JsonValue.of(value != null ? 1 : 0))
                    .put("updatedExisting", JsonValue.of(value != null)));
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(true);
        }
    }

    private void executeMultipleStatements(Connection conn, String sql) throws SQLException {
        String[] statements = sql.split(";\n");
        try (Statement stmt = conn.createStatement()) {
            for (String s : statements) {
                if (!s.trim().isEmpty()) {
                    stmt.execute(s.trim() + ";");
                }
            }
        }
    }

    private String extractTypeString(JsonValue typeValue) {
        if (typeValue.isString()) {
            return typeValue.getString();
        } else if (typeValue.isJsonArray()) {
            JsonArray typeArray = typeValue.getJsonArray();
            for (int i = 0; i < typeArray.size(); i++) {
                String t = typeArray.get(i).getString();
                if (!"null".equals(t)) {
                    return t;
                }
            }
            return "null";
        }
        return null;
    }

    @Override
    public void close() throws Exception {
        // No-op: connections are per-call and managed internally
    }
}
