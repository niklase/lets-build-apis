package com.zuunr.mongodb;

import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import org.bson.Document;

public class DropCommandTranslator {

    public Document translate(JsonObject deleteCommand) {
        Document translated = new Document();
        String collection = deleteCommand.get("collection", JsonValue.NULL).getString();
        translated.put("drop", collection);
        return translated;
    }


}
