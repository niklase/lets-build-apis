package com.zuunr.mongodb;

import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;

public class AggregateCommandTranslator extends AbstractCommandTranslator {

    private Json2BsonQueryTranslator queryTranslator = new Json2BsonQueryTranslator();

    public Document translate(JsonObject aggregateOperand) {
        Document translated = new Document();

        String collection = aggregateOperand.get("collection", JsonValue.NULL).getString();
        translated.put("aggregate", collection == null ? 1 : collection); // collection name

        JsonArray pipeline = aggregateOperand.get("pipeline").getJsonArray();
        List<Document> pipelineTranslated = translateDocuments(pipeline);//translatePipeline(pipeline);//translateDocuments(pipeline);
        translated.put("pipeline", pipelineTranslated);

        JsonObject cursor = aggregateOperand.get("cursor").getJsonObject();
        Document cursorTranslated = translateDocuments(cursor.jsonValue()).get(0);
        translated.put("cursor", cursorTranslated);

        return translated;
    }

    private List<Document> translatePipeline(JsonArray pipeline) {
        List<Document> translated = new ArrayList<>(pipeline.size());
        for (int i = 0; i < pipeline.size(); i++) {
            Document translatedPipelineStage;
            JsonObject pipelineStage = pipeline.get(i).getJsonObject();
            String stageOperator = pipelineStage.keys().get(0).getString();
            JsonValue stageOperand = pipelineStage.values().get(0);
            translatedPipelineStage = new Document();
            switch (stageOperator) {
                case "$match": {
                    translatedPipelineStage.put(stageOperator, queryTranslator.translateQuery(stageOperand.getJsonObject()));
                    continue;
                } case "$pipeline":{
                    translatedPipelineStage.put(stageOperator, translatePipeline(stageOperand.getJsonArray()));
                    continue;
                } default:{
                    translatedPipelineStage.put(stageOperator, translateDocuments(stageOperand).get(0));
                }
            }
            translated.add(translatedPipelineStage);
        }
        return translated;
    }

}


