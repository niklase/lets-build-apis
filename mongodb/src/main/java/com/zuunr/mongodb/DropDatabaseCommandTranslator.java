package com.zuunr.mongodb;

import com.zuunr.json.JsonObject;
import org.bson.Document;

public class DropDatabaseCommandTranslator {
    public Document translate(JsonObject dropDatabaseCommand) {
        Document translated = new Document();
        translated.put("dropDatabase", 1);
        return translated;
    }
}
