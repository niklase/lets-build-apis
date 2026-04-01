package com.zuunr.mongodb;

import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import org.bson.Document;

import java.util.List;

public class InsertCommandTranslator extends AbstractCommandTranslator {

    public Document translate(JsonObject insertCommand) {
        Document translated = new Document();

        String collection = insertCommand.get("collection", JsonValue.NULL).getString();
        if (collection == null) {
            collection = insertCommand.get("insert").getString();
        }
        translated.put("insert", collection); // collection name

        JsonArray documents = insertCommand.get("documents").getJsonArray();
        List documentsTranslated = translateDocuments(documents);
        translated.put("documents", documentsTranslated);
        return translated;
    }
}
