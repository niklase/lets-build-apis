package com.zuunr.sqlschema;

import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Translates a MongoDB-style find command (same format as com.zuunr.mongodb) into a
 * SQL SELECT statement targeting the schema produced by {@link FlywayMigrationGenerator}.
 *
 * Input is the inner find-args object: collection, filter, sort, skip, limit.
 * Dot-notation field paths (e.g. "address.city") are mapped to the double-underscore
 * column names the schema generator produces (e.g. address__city).
 *
 * Supported filter operators: $eq, $ne, $gt, $gte, $lt, $lte, $in, $nin, $regex,
 * $and, $or, $nor.
 */
public class SqlQueryGenerator {

    public static final class PreparedQuery {
        public final String sql;
        public final List<Object> params;

        public PreparedQuery(String sql, List<Object> params) {
            this.sql = sql;
            this.params = params;
        }
    }

    public String generateSelect(JsonObject findArgs) {
        String table = findArgs.get("collection").getString();
        StringBuilder sql = new StringBuilder("SELECT * FROM ").append(table);

        JsonObject filter = findArgs.get("filter", JsonValue.NULL).getJsonObject();
        if (filter != null && !filter.isEmpty()) {
            sql.append(" WHERE ").append(whereClause(filter));
        }

        JsonArray sort = findArgs.get("sort", JsonValue.NULL).getJsonArray();
        if (sort != null && !sort.isEmpty()) {
            sql.append(" ORDER BY ").append(orderByClause(sort));
        }

        Integer skip = findArgs.get("skip", JsonValue.NULL).getInteger();
        Integer limit = findArgs.get("limit", JsonValue.NULL).getInteger();

        if (limit != null || skip != null) {
            if (sort == null || sort.isEmpty()) {
                sql.append(" ORDER BY (SELECT NULL)");
            }
            sql.append(" OFFSET ").append(skip != null ? skip : 0).append(" ROWS");
            if (limit != null) {
                sql.append(" FETCH NEXT ").append(limit).append(" ROWS ONLY");
            }
        }

        return sql.toString();
    }

    public PreparedQuery generatePreparedSelect(JsonObject findArgs) {
        String table = findArgs.get("collection").getString();
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT * FROM ").append(table);

        JsonObject filter = findArgs.get("filter", JsonValue.NULL).getJsonObject();
        if (filter != null && !filter.isEmpty()) {
            sql.append(" WHERE ").append(whereClausePrepared(filter, params));
        }

        JsonArray sort = findArgs.get("sort", JsonValue.NULL).getJsonArray();
        if (sort != null && !sort.isEmpty()) {
            sql.append(" ORDER BY ").append(orderByClause(sort));
        }

        Integer skip = findArgs.get("skip", JsonValue.NULL).getInteger();
        Integer limit = findArgs.get("limit", JsonValue.NULL).getInteger();

        if (limit != null || skip != null) {
            if (sort == null || sort.isEmpty()) {
                sql.append(" ORDER BY (SELECT NULL)");
            }
            sql.append(" OFFSET ").append(skip != null ? skip : 0).append(" ROWS");
            if (limit != null) {
                sql.append(" FETCH NEXT ").append(limit).append(" ROWS ONLY");
            }
        }

        return new PreparedQuery(sql.toString(), params);
    }

    public JsonArray queryDocuments(Connection conn, JsonObject findArgs, JsonObject schema) throws SQLException {
        String collection = findArgs.get("collection").getString();
        PreparedQuery pq = generatePreparedSelect(findArgs);

        JsonArray results = JsonArray.EMPTY;
        try (PreparedStatement ps = conn.prepareStatement(pq.sql)) {
            for (int i = 0; i < pq.params.size(); i++) {
                ps.setObject(i + 1, pq.params.get(i));
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    JsonObject doc = reconstructDocument(rs, collection, schema, conn);
                    results = results.add(doc.jsonValue());
                }
            }
        }
        return results;
    }

    public PreparedQuery generatePreparedDelete(String collection, JsonObject filter) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("DELETE FROM ").append(collection);

        if (filter != null && !filter.isEmpty()) {
            sql.append(" WHERE ").append(whereClausePrepared(filter, params));
        }

        return new PreparedQuery(sql.toString(), params);
    }

    private String whereClause(JsonObject query) {
        String key = query.keys().head().getString();
        return key.startsWith("$") ? translateLogical(query) : translateFieldCriteria(query);
    }

    private String whereClausePrepared(JsonObject query, List<Object> params) {
        String key = query.keys().head().getString();
        return key.startsWith("$") ? translateLogicalPrepared(query, params) : translateFieldCriteriaPrepared(query, params);
    }

    private String translateLogical(JsonObject operatorObj) {
        String op = operatorObj.keys().head().getString();
        JsonArray operands = operatorObj.values().head().getJsonArray();
        boolean nor = "$nor".equals(op);
        String sqlOp = (nor || "$and".equals(op)) ? " AND " : " OR ";

        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < operands.size(); i++) {
            if (i > 0) sb.append(sqlOp);
            String part = whereClause(operands.get(i).getJsonObject());
            sb.append(nor ? "NOT (" + part + ")" : part);
        }
        return sb.append(")").toString();
    }

    private String translateLogicalPrepared(JsonObject operatorObj, List<Object> params) {
        String op = operatorObj.keys().head().getString();
        JsonArray operands = operatorObj.values().head().getJsonArray();
        boolean nor = "$nor".equals(op);
        String sqlOp = (nor || "$and".equals(op)) ? " AND " : " OR ";

        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < operands.size(); i++) {
            if (i > 0) sb.append(sqlOp);
            String part = whereClausePrepared(operands.get(i).getJsonObject(), params);
            sb.append(nor ? "NOT (" + part + ")" : part);
        }
        return sb.append(")").toString();
    }

    private String translateFieldCriteria(JsonObject fieldCriteria) {
        String column = fieldCriteria.keys().head().getString().replace(".", "__");
        JsonArray comparisons = fieldCriteria.values().head().getJsonArray();

        if (comparisons.size() == 1) {
            return translateComparison(column, comparisons.get(0).getJsonObject());
        }
        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < comparisons.size(); i++) {
            if (i > 0) sb.append(" AND ");
            sb.append(translateComparison(column, comparisons.get(i).getJsonObject()));
        }
        return sb.append(")").toString();
    }

    private String translateFieldCriteriaPrepared(JsonObject fieldCriteria, List<Object> params) {
        String column = fieldCriteria.keys().head().getString().replace(".", "__");
        JsonArray comparisons = fieldCriteria.values().head().getJsonArray();

        if (comparisons.size() == 1) {
            return translateComparisonPrepared(column, comparisons.get(0).getJsonObject(), params);
        }
        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < comparisons.size(); i++) {
            if (i > 0) sb.append(" AND ");
            sb.append(translateComparisonPrepared(column, comparisons.get(i).getJsonObject(), params));
        }
        return sb.append(")").toString();
    }

    private String translateComparison(String column, JsonObject compOp) {
        String op = compOp.keys().head().getString();
        JsonValue val = compOp.values().head();
        switch (op) {
            case "$eq":    return column + " = " + literal(val);
            case "$ne":    return column + " <> " + literal(val);
            case "$gt":    return column + " > " + literal(val);
            case "$gte":   return column + " >= " + literal(val);
            case "$lt":    return column + " < " + literal(val);
            case "$lte":   return column + " <= " + literal(val);
            case "$in":    return column + " IN (" + inList(val.getJsonArray()) + ")";
            case "$nin":   return column + " NOT IN (" + inList(val.getJsonArray()) + ")";
            case "$regex": return "PATINDEX('%" + val.getString() + "%', " + column + ") > 0";
            default: throw new RuntimeException("Unsupported operator: " + op);
        }
    }

    private String translateComparisonPrepared(String column, JsonObject compOp, List<Object> params) {
        String op = compOp.keys().head().getString();
        JsonValue val = compOp.values().head();
        switch (op) {
            case "$eq":
                params.add(toObjectValue(val));
                return column + " = ?";
            case "$ne":
                params.add(toObjectValue(val));
                return column + " <> ?";
            case "$gt":
                params.add(toObjectValue(val));
                return column + " > ?";
            case "$gte":
                params.add(toObjectValue(val));
                return column + " >= ?";
            case "$lt":
                params.add(toObjectValue(val));
                return column + " < ?";
            case "$lte":
                params.add(toObjectValue(val));
                return column + " <= ?";
            case "$in":
                JsonArray arr = val.getJsonArray();
                StringBuilder inClause = new StringBuilder(column).append(" IN (");
                for (int i = 0; i < arr.size(); i++) {
                    if (i > 0) inClause.append(", ");
                    params.add(toObjectValue(arr.get(i)));
                    inClause.append("?");
                }
                return inClause.append(")").toString();
            case "$nin":
                JsonArray ninArr = val.getJsonArray();
                StringBuilder ninClause = new StringBuilder(column).append(" NOT IN (");
                for (int i = 0; i < ninArr.size(); i++) {
                    if (i > 0) ninClause.append(", ");
                    params.add(toObjectValue(ninArr.get(i)));
                    ninClause.append("?");
                }
                return ninClause.append(")").toString();
            case "$regex":
                params.add("%" + val.getString() + "%");
                return "PATINDEX(?, " + column + ") > 0";
            default: throw new RuntimeException("Unsupported operator: " + op);
        }
    }

    private String inList(JsonArray array) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < array.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(literal(array.get(i)));
        }
        return sb.toString();
    }

    private String orderByClause(JsonArray sort) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sort.size(); i++) {
            if (i > 0) sb.append(", ");
            JsonObject item = sort.get(i).getJsonObject();
            String field = item.keys().head().getString().replace(".", "__");
            Integer dir = item.values().head().getInteger();
            sb.append(field).append(dir != null && dir < 0 ? " DESC" : " ASC");
        }
        return sb.toString();
    }

    private String literal(JsonValue value) {
        if (value.isNull())       return "NULL";
        if (value.isString())     return "'" + value.getString().replace("'", "''") + "'";
        if (value.isInteger())    return value.getInteger().toString();
        if (value.isBigDecimal()) return value.getBigDecimal().toPlainString();
        if (value.isBoolean())    return value.getBoolean() ? "1" : "0";
        return "NULL";
    }

    private Object toObjectValue(JsonValue value) {
        if (value.isNull())       return null;
        if (value.isString())     return value.getString();
        if (value.isInteger())    return value.getInteger();
        if (value.isBigDecimal()) return value.getBigDecimal().doubleValue();
        if (value.isBoolean())    return value.getBoolean();
        return null;
    }

    private JsonValue toJsonValue(Object obj) {
        if (obj == null) return JsonValue.NULL;
        if (obj instanceof String) return JsonValue.of((String) obj);
        if (obj instanceof Long) return JsonValue.of((Long) obj);
        if (obj instanceof Integer) return JsonValue.of(((Integer) obj).longValue());
        if (obj instanceof Double) return JsonValue.of(BigDecimal.valueOf((Double) obj));
        if (obj instanceof Float) return JsonValue.of(BigDecimal.valueOf(((Float) obj).doubleValue()));
        if (obj instanceof Boolean) return JsonValue.of((Boolean) obj);
        if (obj instanceof BigDecimal) return JsonValue.of((BigDecimal) obj);
        return JsonValue.of(obj.toString());
    }

    private JsonObject reconstructDocument(ResultSet rs, String rootTable, JsonObject schema, Connection conn) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        Map<String, Object> rowMap = new HashMap<>();
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            rowMap.put(meta.getColumnName(i).toLowerCase(), rs.getObject(i));
        }

        JsonObject doc = JsonObject.EMPTY;
        JsonObject properties = schema.get("properties", JsonValue.NULL).getJsonObject();
        if (properties == null) return doc;

        for (int i = 0; i < properties.keys().size(); i++) {
            String fieldName = properties.keys().get(i).getString();
            JsonObject propSchema = properties.values().get(i).getJsonObject();
            String type = extractTypeString(propSchema.get("type", JsonValue.NULL));

            if ("object".equals(type)) {
                JsonObject nested = JsonObject.EMPTY;
                String prefix = fieldName + "__";
                for (Map.Entry<String, Object> entry : rowMap.entrySet()) {
                    if (entry.getKey().startsWith(prefix)) {
                        String subField = entry.getKey().substring(prefix.length());
                        if (entry.getValue() != null) {
                            nested = nested.put(subField, toJsonValue(entry.getValue()));
                        }
                    }
                }
                if (!nested.isEmpty()) {
                    doc = doc.put(fieldName, nested);
                }
            } else if ("array".equals(type)) {
                JsonArray array = JsonArray.EMPTY;
                String childTable = rootTable + "_" + fieldName;
                String parentIdCol = rootTable + "_id";
                String parentId = (String) rowMap.get("id");

                String childSql = "SELECT * FROM " + childTable + " WHERE " + parentIdCol + " = ? ORDER BY position";
                try (PreparedStatement childPs = conn.prepareStatement(childSql)) {
                    childPs.setString(1, parentId);
                    try (ResultSet childRs = childPs.executeQuery()) {
                        JsonObject itemsSchema = propSchema.get("items", JsonValue.NULL).getJsonObject();
                        String itemsType = itemsSchema != null ? itemsSchema.get("type", JsonValue.NULL).getString() : null;

                        while (childRs.next()) {
                            if ("object".equals(itemsType)) {
                                JsonObject item = reconstructArrayItem(childRs, itemsSchema);
                                array = array.add(item);
                            } else {
                                JsonValue value = toJsonValue(childRs.getObject("value"));
                                array = array.add(value);
                            }
                        }
                    }
                }
                doc = doc.put(fieldName, array);
            } else {
                String colName = fieldName.toLowerCase();
                Object value = rowMap.get(colName);
                if (value != null) {
                    doc = doc.put(fieldName, toJsonValue(value));
                }
            }
        }
        return doc;
    }

    private JsonObject reconstructArrayItem(ResultSet rs, JsonObject itemsSchema) throws SQLException {
        JsonObject item = JsonObject.EMPTY;
        JsonObject properties = itemsSchema.get("properties", JsonValue.NULL).getJsonObject();
        if (properties == null) return item;

        for (int i = 0; i < properties.keys().size(); i++) {
            String fieldName = properties.keys().get(i).getString();
            try {
                Object value = rs.getObject(fieldName);
                if (value != null) {
                    item = item.put(fieldName, toJsonValue(value));
                }
            } catch (Exception e) {
                // Column might not exist (e.g., FK columns, position column)
                // Skip it
            }
        }
        return item;
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
