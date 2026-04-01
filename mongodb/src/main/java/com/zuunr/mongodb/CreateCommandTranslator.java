package com.zuunr.mongodb;

import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import org.bson.Document;

public class CreateCommandTranslator {
    public Document translate(JsonObject command) {
        Document translated = new Document();

        String collectionOrViewName = command.get("collection", JsonValue.NULL).getString();
        translated.put("create", collectionOrViewName); // collection name
        return translated;
    }

}
