package com.zuunr.sqlschema;

import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
 * Generates SQL Server MERGE statements from MongoDB-style insert commands (same format as
 * com.zuunr.mongodb insert commands).
 *
 * Documents are treated as root aggregates: every scalar and nested-object field is
 * written as a flat column (address.city → address__city). Arrays are written by
 * first deleting all existing rows in the auxiliary table for this id, then
 * inserting the new values — preserving the whole-aggregate-write contract.
 *
 * Generated SQL targets SQL Server MERGE syntax.
 */
public class SqlUpsertGenerator {

    public String generateUpsert(JsonObject insertCommand) {
        JsonObject insert = insertCommand.get("insert", JsonValue.NULL).getJsonObject();
        if (insert == null) insert = insertCommand;

        String table = insert.get("collection").getString();
        JsonArray documents = insert.get("documents").getJsonArray();

        StringBuilder sql = new StringBuilder();
        for (int i = 0; i < documents.size(); i++) {
            if (i > 0) sql.append("\n");
            sql.append(upsertDocument(table, documents.get(i).getJsonObject()));
        }
        return sql.toString();
    }

    private String upsertDocument(String table, JsonObject document) {
        String parentId = document.get("id", JsonValue.NULL).getString();

        List<String> columns = new ArrayList<>();
        List<String> values = new ArrayList<>();
        flattenScalars(document, "", columns, values);

        List<String> auxiliaryStatements = new ArrayList<>();
        collectArrayStatements(table, document, "", parentId, auxiliaryStatements);

        StringBuilder sb = new StringBuilder();

        // MERGE ... AS target
        sb.append("MERGE ").append(table).append(" AS target\n");

        // USING (SELECT val AS col, ...) AS source
        sb.append("USING (SELECT ");
        StringJoiner sourceSelects = new StringJoiner(", ");
        for (int i = 0; i < columns.size(); i++) {
            sourceSelects.add(values.get(i) + " AS " + columns.get(i));
        }
        sb.append(sourceSelects).append(") AS source\n");

        sb.append("ON target.id = source.id\n");

        // WHEN MATCHED → UPDATE all non-id columns
        List<String> updateSets = new ArrayList<>();
        for (String col : columns) {
            if (!"id".equals(col)) {
                updateSets.add("        target." + col + " = source." + col);
            }
        }
        if (!updateSets.isEmpty()) {
            sb.append("WHEN MATCHED THEN\n    UPDATE SET\n");
            sb.append(String.join(",\n", updateSets)).append("\n");
        }

        // WHEN NOT MATCHED → INSERT
        sb.append("WHEN NOT MATCHED THEN\n    INSERT (");
        sb.append(String.join(", ", columns));
        sb.append(")\n    VALUES (");
        StringJoiner sourceValues = new StringJoiner(", ");
        for (String col : columns) sourceValues.add("source." + col);
        sb.append(sourceValues).append(");\n");

        for (String aux : auxiliaryStatements) {
            sb.append("\n").append(aux);
        }
        return sb.toString();
    }

    private void flattenScalars(JsonObject doc, String prefix,
                                List<String> columns, List<String> values) {
        for (int i = 0; i < doc.keys().size(); i++) {
            String key = doc.keys().get(i).getString();
            JsonValue val = doc.values().get(i);
            String col = prefix.isEmpty() ? key : prefix + "__" + key;

            if (val.isJsonObject()) {
                flattenScalars(val.getJsonObject(), col, columns, values);
            } else if (!val.isJsonArray()) {
                columns.add(col);
                values.add(literal(val));
            }
            // arrays are handled separately via collectArrayStatements
        }
    }

    private void collectArrayStatements(String rootTable, JsonObject doc, String prefix,
                                        String parentId, List<String> statements) {
        for (int i = 0; i < doc.keys().size(); i++) {
            String key = doc.keys().get(i).getString();
            JsonValue val = doc.values().get(i);
            String col = prefix.isEmpty() ? key : prefix + "__" + key;

            if (val.isJsonObject()) {
                collectArrayStatements(rootTable, val.getJsonObject(), col, parentId, statements);
                continue;
            }

            if (!val.isJsonArray()) continue;
            JsonArray array = val.getJsonArray();

            String auxTable = rootTable + "_" + col.replace("__", "_");
            StringBuilder aux = new StringBuilder();
            aux.append("DELETE FROM ").append(auxTable)
               .append(" WHERE ").append(rootTable).append("_id = '").append(parentId).append("';\n");

            for (int j = 0; j < array.size(); j++) {
                JsonValue item = array.get(j);
                JsonObject itemObj = item.isJsonObject() ? item.getJsonObject() : null;
                if (itemObj != null) {
                    List<String> objCols = new ArrayList<>();
                    List<String> objVals = new ArrayList<>();
                    flattenScalars(itemObj, "", objCols, objVals);
                    aux.append("INSERT INTO ").append(auxTable).append(" (")
                       .append(rootTable).append("_id, position");
                    for (String c : objCols) aux.append(", ").append(c);
                    aux.append(") VALUES ('").append(parentId).append("', ").append(j);
                    for (String v : objVals) aux.append(", ").append(v);
                    aux.append(");\n");
                } else {
                    aux.append("INSERT INTO ").append(auxTable)
                       .append(" (").append(rootTable).append("_id, position, value) VALUES ('")
                       .append(parentId).append("', ").append(j).append(", ").append(literal(item)).append(");\n");
                }
            }
            statements.add(aux.toString());
        }
    }

    private String literal(JsonValue value) {
        if (value.isNull())       return "NULL";
        if (value.isString())     return "'" + value.getString().replace("'", "''") + "'";
        if (value.isInteger())    return value.getInteger().toString();
        if (value.isBigDecimal()) return value.getBigDecimal().toPlainString();
        if (value.isBoolean())    return value.getBoolean() ? "1" : "0";
        return "NULL";
    }
}
