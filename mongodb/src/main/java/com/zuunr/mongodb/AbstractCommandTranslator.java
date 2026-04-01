package com.zuunr.mongodb;

import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;

public class AbstractCommandTranslator {


    protected List<Document> translateDocuments(JsonValue documentOrDocuments) {
        if (documentOrDocuments.isJsonArray()) {
            return translateDocuments(documentOrDocuments.getJsonArray());
        } else {
            return translateDocuments(JsonArray.of(documentOrDocuments));
        }
    }

    protected List<Document> translateDocuments(JsonArray documents) {
        ArrayList<Document> list = new ArrayList<>();
        for (int i = 0; i < documents.size(); i++) {
            JsonObject document = documents.get(i).getJsonObject();
            list.add(Json2BsonTranslator.translate(document));
        }
        return list;
    }
}
