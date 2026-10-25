package com.zuunr.mongodb;

import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;

public class InsertCommandTranslator {

    private final CollectionSchemas collectionSchemas;

    public InsertCommandTranslator(CollectionSchemas collectionSchemas) {
        this.collectionSchemas = collectionSchemas;
    }

    public Document translate(JsonObject insertCommand) {
        Document translated = new Document();

        String collection = insertCommand.get("collection", JsonValue.NULL).getString();
        if (collection == null) {
            collection = insertCommand.get("insert").getString();
        }
        translated.put("insert", collection); // collection name

        JsonArray documents = insertCommand.get("documents").getJsonArray();
        List<Document> documentsTranslated = translateDocuments(collection, documents);
        translated.put("documents", documentsTranslated);
        return translated;
    }

    private List<Document> translateDocuments(String collection, JsonArray documents) {
        List<Document> list = new ArrayList<>();
        for (int i = 0; i < documents.size(); i++) {
            list.add(collectionSchemas.translateDocument(collection, documents.get(i).getJsonObject()));
        }
        return list;
    }
}
