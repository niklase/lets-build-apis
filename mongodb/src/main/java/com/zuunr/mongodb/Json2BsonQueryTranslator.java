package com.zuunr.mongodb;

import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;

public class Json2BsonQueryTranslator {

    /**
     * @param query with exactly one field which may be either a field name of the query or an operator. e.g <code>{"age": [{"$gt": 18}]}</code> or {"$and": [{"age": [{"$gt": 18}]}]}
     * @return
     */
    public Document translateQuery(JsonObject query) {
        String key = query.keys().head().getString();
        Document document;
        if (isOperator(key)) {
            document = translateOperator(query);
        } else {
            document = translateFieldCriteria(query);
        }
        return document;
    }

    /**
     * @param operatorObject e.g <code>{"$and":[{"name":{"$eq": "Peter"}}]}</code> or <br/><code>{"$eq": "Peter"}</code>
     * @return
     */
    public Document translateOperator(JsonObject operatorObject) {
        String operator = operatorObject.keys().head().getString();
        JsonArray operatorValue = operatorObject.values().head().getJsonArray();
        Document translated;
        if (isLogicalOperator(operator)) {
            translated = translateLogicalOperator(operator, operatorValue);
        } else if (isComparisonQueryOperator(operator)) {
            translated = translateComparisonQueryOperator(operatorObject);
        } else {
            throw new RuntimeException("Operator is not supported: " + operator);
        }
        return translated;
    }

    private Document translateLogicalOperator(String operator, JsonArray operatorValue) {
        if ("$not".equals(operator)) {
            throw new RuntimeException("Operator not supported: " + operator);
        }

        List<Document> translatedList = new ArrayList<>(operatorValue.size());
        for (int i = 0; i < operatorValue.size(); i++) {
            translatedList.add(translateQuery(operatorValue.get(i).getJsonObject()));
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
     * @param orderedComparisonOperators JsonArray where each item is a JsonObject with exactly one field representing the comparison operator, e.g [{"$eq":"foo"}]
     * @return
     */
    public Document translateComparisonQueryOperators(JsonArray orderedComparisonOperators) {
        Document translated = new Document();
        for (int i = 0; i < orderedComparisonOperators.size(); i++) {
            JsonObject item = orderedComparisonOperators.get(i).getJsonObject();
            JsonValue value = item.values().head();
            Object bsonValue = Json2BsonTranslator.translate( // TODO: Make Json2BsonTranslator.translate handle JsonValue instead of just JsonObject
                    JsonObject.EMPTY.put("value", value)
            ).get("value");
            translated.put(item.keys().head().getString(), bsonValue);
        }
        return translated;
    }

    /**
     * @param fieldCriteria e.g JSON: <code>{"age":[{"$lt": 26}, {"$gt": 16}]}</code>
     * @return e.g BSON <code>{age: {$lt: 26, $gt": 16}}</code>
     */
    public Document translateFieldCriteria(JsonObject fieldCriteria) {
        Document doc = new Document();
        doc.put(fieldCriteria.keys().head().getString(),
                translateComparisonQueryOperators(fieldCriteria.values().head().getJsonArray()));
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
