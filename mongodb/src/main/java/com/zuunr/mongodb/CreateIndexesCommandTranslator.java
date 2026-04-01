package com.zuunr.mongodb;

import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import org.bson.Document;

import java.util.List;

public class CreateIndexesCommandTranslator extends AbstractCommandTranslator {

    public Document translate(JsonObject command) {
        Document translated = new Document();

        String collection = command.get("collection", JsonValue.NULL).getString();
        translated.put("createIndexes", collection); // collection name

        JsonArray indexes = command.get("indexes").getJsonArray();
        List indexesTranslated = translateDocuments(indexes);
        translated.put("indexes", indexesTranslated);
        return translated;
    }
}
