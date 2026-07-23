package com.zuunr.sqlschema;

import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Generates Flyway SQL migration scripts from a JSON Schema using a proper relational model.
 *
 * Design rules (as a SQL DBA would):
 * - The root JSON object maps to a root table with {@code id VARCHAR(255)} as primary key.
 * - Scalar nested objects are flattened into the parent table using double-underscore
 *   column names (e.g. address.city → address__city).
 * - Arrays at any nesting level produce a dedicated child table.
 * - Each child table carries all ancestor key columns as a composite primary key, plus
 *   its own {@code position} column to preserve array ordering.
 * - A composite FOREIGN KEY back to the immediate parent is declared with ON DELETE CASCADE,
 *   enforcing the root-aggregate write contract (delete root → cascade to all children).
 * - Arrays nested inside other arrays recurse: each level adds one more position column
 *   to the composite PK, and the FK references the direct parent's composite PK.
 *
 * Example:
 * <pre>
 * customer(id PK)
 * customer_orders(customer_id, position → PK; FK customer_id→customer.id)
 * customer_orders_items(customer_id, orders_position, position → PK;
 *                       FK (customer_id, orders_position)→customer_orders(customer_id, position))
 * </pre>
 */
public class FlywayMigrationGenerator {

    private static final class ParentRef {
        final String localCol;   // column name in the child table
        final String parentCol;  // column name in the parent table (for FK reference)
        final String sqlType;
        ParentRef(String localCol, String parentCol, String sqlType) {
            this.localCol = localCol;
            this.parentCol = parentCol;
            this.sqlType = sqlType;
        }
    }

    private static final class ArrayField {
        final String tableSuffix;    // appended to parent table name, e.g. "orders" or "address_contacts"
        final JsonObject itemSchema;
        ArrayField(String columnPath, JsonObject itemSchema) {
            this.tableSuffix = columnPath.replace("__", "_");
            this.itemSchema = itemSchema;
        }
    }

    public String generateCreateTable(String tableName, JsonObject schema) {
        List<String> statements = new ArrayList<>();
        generateTable(tableName, schema, Collections.emptyList(), null, statements);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < statements.size(); i++) {
            if (i > 0) sb.append("\n");
            sb.append(statements.get(i));
        }
        return sb.toString();
    }

    public String generateCreateTableIfNotExists(String tableName, JsonObject schema) {
        String ddl = generateCreateTable(tableName, schema);
        // Wrap each CREATE TABLE statement in IF OBJECT_ID check
        String[] statements = ddl.split(";\n");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < statements.length; i++) {
            String stmt = statements[i].trim();
            if (!stmt.isEmpty()) {
                if (i > 0) sb.append("\n");
                // Extract table name from "CREATE TABLE tableName ("
                String extractedTableName = extractTableNameFromCreateStatement(stmt);
                sb.append("IF OBJECT_ID(N'").append(extractedTableName).append("', N'U') IS NULL BEGIN\n");
                sb.append("    ").append(stmt.replace("\n", "\n    "));
                sb.append("\nEND;");
            }
        }
        return sb.toString();
    }

    private String extractTableNameFromCreateStatement(String createTableStmt) {
        // Parse "CREATE TABLE tableName (" to get tableName
        String trimmed = createTableStmt.trim();
        int start = trimmed.indexOf("CREATE TABLE") + "CREATE TABLE".length();
        int end = trimmed.indexOf("(", start);
        return trimmed.substring(start, end).trim();
    }

    private void generateTable(String tableName, JsonObject schema,
                               List<ParentRef> parentRefs, String parentTable,
                               List<String> statements) {
        boolean isRoot = parentTable == null;
        List<String> defs = new ArrayList<>();
        List<ArrayField> arrays = new ArrayList<>();

        // Inherited key columns (FK-carrying columns from all ancestor tables)
        for (ParentRef ref : parentRefs) {
            defs.add(ref.localCol + " " + ref.sqlType + " NOT NULL");
        }

        // Root's primary key column, or child's position within its parent array
        if (isRoot) {
            defs.add("id NVARCHAR(255) NOT NULL");
        } else {
            defs.add("position INTEGER NOT NULL");
        }

        // Scalar fields and sub-arrays
        JsonObject properties = schema.get("properties", JsonValue.NULL).getJsonObject();
        if (properties != null) {
            collectFields(schema, "", isRoot, defs, arrays);
        } else if (!isRoot) {
            // Scalar array: the single item value
            String itemType = extractTypeString(schema.get("type", JsonValue.NULL));
            defs.add("value " + toSqlType(itemType));
        }

        // PRIMARY KEY — all inherited ref columns + own key (id or position)
        List<String> pkCols = new ArrayList<>();
        for (ParentRef ref : parentRefs) pkCols.add(ref.localCol);
        pkCols.add(isRoot ? "id" : "position");
        defs.add("PRIMARY KEY (" + String.join(", ", pkCols) + ")");

        // FK constraint referencing the immediate parent
        if (!isRoot) {
            List<String> localCols = new ArrayList<>();
            List<String> refCols = new ArrayList<>();
            for (ParentRef ref : parentRefs) {
                localCols.add(ref.localCol);
                refCols.add(ref.parentCol);
            }
            defs.add("FOREIGN KEY (" + String.join(", ", localCols) +
                    ") REFERENCES " + parentTable +
                    "(" + String.join(", ", refCols) + ") ON DELETE CASCADE");
        }

        statements.add(buildCreateTable(tableName, defs));

        // Recursively generate child tables for discovered array fields
        for (ArrayField array : arrays) {
            String childTable = tableName + "_" + array.tableSuffix;

            List<ParentRef> childRefs = new ArrayList<>();

            // Propagate all inherited refs unchanged into the grandchild
            for (ParentRef ref : parentRefs) {
                childRefs.add(new ParentRef(ref.localCol, ref.localCol, ref.sqlType));
            }

            // This table's own key becomes a ref in the child, renamed to avoid collision
            if (isRoot) {
                // Root passes its 'id' as 'tableName_id'
                childRefs.add(new ParentRef(tableName + "_id", "id", "NVARCHAR(255)"));
            } else {
                // Child table passes its 'position' as 'lastTableSegment_position'
                String lastSeg = tableName.contains("_")
                        ? tableName.substring(tableName.lastIndexOf('_') + 1)
                        : tableName;
                childRefs.add(new ParentRef(lastSeg + "_position", "position", "INTEGER"));
            }

            generateTable(childTable, array.itemSchema, childRefs, tableName, statements);
        }
    }

    private void collectFields(JsonObject schema, String prefix, boolean skipRootId,
                               List<String> defs, List<ArrayField> arrays) {
        JsonObject properties = schema.get("properties", JsonValue.NULL).getJsonObject();
        if (properties == null) return;

        for (int i = 0; i < properties.keys().size(); i++) {
            String field = properties.keys().get(i).getString();
            if (skipRootId && "id".equals(field) && prefix.isEmpty()) continue;

            JsonObject fieldSchema = properties.values().get(i).getJsonObject();
            String column = prefix.isEmpty() ? field : prefix + "__" + field;
            String type = extractTypeString(fieldSchema.get("type", JsonValue.NULL));

            if ("object".equals(type)) {
                // Flatten nested-object scalar fields into this table; recurse for arrays inside
                collectFields(fieldSchema, column, false, defs, arrays);
            } else if ("array".equals(type)) {
                JsonValue itemsVal = fieldSchema.get("items", JsonValue.NULL);
                JsonObject items = itemsVal.isJsonObject() ? itemsVal.getJsonObject() : JsonObject.EMPTY;
                arrays.add(new ArrayField(column, items));
            } else {
                // All non-root fields are nullable to support absent JSON fields mapping to NULL
                defs.add(column + " " + toSqlType(type));
            }
        }
    }

    private String buildCreateTable(String tableName, List<String> defs) {
        StringBuilder sql = new StringBuilder("CREATE TABLE ").append(tableName).append(" (\n");
        for (int i = 0; i < defs.size(); i++) {
            sql.append("    ").append(defs.get(i));
            if (i < defs.size() - 1) sql.append(",");
            sql.append("\n");
        }
        return sql.append(");\n").toString();
    }

    private boolean containsString(JsonArray array, String value) {
        if (array == null) return false;
        for (int i = 0; i < array.size(); i++) {
            if (value.equals(array.get(i).getString())) return true;
        }
        return false;
    }

    private String toSqlType(String jsonType) {
        if (jsonType == null) return "NVARCHAR(MAX)";
        switch (jsonType) {
            case "string":  return "NVARCHAR(255)";
            case "integer": return "BIGINT";
            case "number":  return "FLOAT";
            case "boolean": return "BIT";
            default:        return "NVARCHAR(MAX)";
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
}
