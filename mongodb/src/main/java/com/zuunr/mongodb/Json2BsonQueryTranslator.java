package com.zuunr.mongodb;

import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;

public class Json2BsonQueryTranslator {

    private final CollectionSchemas collectionSchemas;

    public Json2BsonQueryTranslator(CollectionSchemas collectionSchemas) {
        this.collectionSchemas = collectionSchemas;
    }

    /**
     * @param collection the collection this query/filter is run against - used to look up
     *                   its schema so field criteria can be translated with the field's own
     *                   BSON type rather than inferred from the query literal's JSON type.
     * @param query      with exactly one field which may be either a field name of the query or an operator. e.g <code>{"age": [{"$gt": 18}]}</code> or {"$and": [{"age": [{"$gt": 18}]}]}
     * @return
     */
    public Document translateQuery(String collection, JsonObject query) {
        String key = query.keys().head().getString();
        Document document;
        if (isOperator(key)) {
            document = translateOperator(collection, query);
        } else {
            document = translateFieldCriteria(collection, query);
        }
        return document;
    }

    /**
     * @param operatorObject e.g <code>{"$and":[{"name":{"$eq": "Peter"}}]}</code> or <br/><code>{"$eq": "Peter"}</code>
     * @return
     */
    public Document translateOperator(String collection, JsonObject operatorObject) {
        String operator = operatorObject.keys().head().getString();
        JsonArray operatorValue = operatorObject.values().head().getJsonArray();
        Document translated;
        if (isLogicalOperator(operator)) {
            translated = translateLogicalOperator(collection, operator, operatorValue);
        } else if (isComparisonQueryOperator(operator)) {
            translated = translateComparisonQueryOperator(operatorObject);
        } else {
            throw new RuntimeException("Operator is not supported: " + operator);
        }
        return translated;
    }

    private Document translateLogicalOperator(String collection, String operator, JsonArray operatorValue) {
        if ("$not".equals(operator)) {
            throw new RuntimeException("Operator not supported: " + operator);
        }

        List<Document> translatedList = new ArrayList<>(operatorValue.size());
        for (int i = 0; i < operatorValue.size(); i++) {
            translatedList.add(translateQuery(collection, operatorValue.get(i).getJsonObject()));
        }
        Document translated = new Document();
        translated.put(operator, translatedList);
        return translated;

    }

    private Document translateComparisonQueryOperator(JsonObject operatorObject) {
        return new Document(Json2BsonTranslator.translate(operatorObject));
    }

    private boolean isComparisonQueryOperator(String operator) {
        switch (operator) {
            case "$eq":
                return true;
            case "$gt":
                return true;
            case "$gte":
                return true;
            case "$in":
                return true;
            case "$lt":
                return true;
            case "$lte":
                return true;
            case "$ne":
                return true;
            case "$nin":
                return true;
            default:
                return false;
        }
    }

    /**
     * @param fieldType                   the field's resolved BSON type, or {@code null} when the
     *                                     field has no registered schema or no resolvable leaf type -
     *                                     then every operator value keeps today's default translation.
     * @param orderedComparisonOperators  JsonArray where each item is a JsonObject with exactly one field representing the comparison operator, e.g [{"$eq":"foo"}]
     * @return
     */
    public Document translateComparisonQueryOperators(BsonType fieldType, JsonArray orderedComparisonOperators) {
        Document translated = new Document();
        for (int i = 0; i < orderedComparisonOperators.size(); i++) {
            JsonObject item = orderedComparisonOperators.get(i).getJsonObject();
            String operator = item.keys().head().getString();
            JsonValue value = item.values().head();
            Object bsonValue = fieldType != null && isComparisonQueryOperator(operator)
                    ? coerceOperatorValue(operator, value, fieldType)
                    : translateWithDefaultMapping(value);
            translated.put(operator, bsonValue);
        }
        return translated;
    }

    /**
     * $in/$nin take an array of candidate values, each matched against the field individually -
     * every other comparison operator ($eq, $gt, ...) takes one value shaped like the field itself.
     */
    private Object coerceOperatorValue(String operator, JsonValue value, BsonType fieldType) {
        if ("$in".equals(operator) || "$nin".equals(operator)) {
            List<Object> coerced = new ArrayList<>();
            for (JsonValue candidate : value.getJsonArray()) {
                coerced.add(coerceLeaf(candidate, fieldType));
            }
            return coerced;
        }
        return coerceLeaf(value, fieldType);
    }

    private Object coerceLeaf(JsonValue value, BsonType fieldType) {
        return value.isNull() ? null : Json2BsonConverter.toBson(value, fieldType);
    }

    private Object translateWithDefaultMapping(JsonValue value) {
        // TODO: Make Json2BsonTranslator.translate handle JsonValue instead of just JsonObject
        return Json2BsonTranslator.translate(JsonObject.EMPTY.put("value", value)).get("value");
    }

    /**
     * @param fieldCriteria e.g JSON: <code>{"age":[{"$lt": 26}, {"$gt": 16}]}</code>
     * @return e.g BSON <code>{age: {$lt: 26, $gt": 16}}</code>
     */
    public Document translateFieldCriteria(String collection, JsonObject fieldCriteria) {
        String fieldName = fieldCriteria.keys().head().getString();
        BsonType fieldType = SchemaNode.resolveLeafType(collectionSchemas.rootNodeFor(collection), fieldName);
        Document doc = new Document();
        doc.put(fieldName, translateComparisonQueryOperators(fieldType, fieldCriteria.values().head().getJsonArray()));
        return doc;
    }

    private boolean isLogicalOperator(String operator) {
        switch (operator) {
            case "$and":
                return true;
            case "$or":
                return true;
            case "$not":
                return true;
            case "$nor":
                return true;
            default:
                return false;
        }
    }

    private boolean isOperator(String key) {
        return key.startsWith("$");
    }
}
