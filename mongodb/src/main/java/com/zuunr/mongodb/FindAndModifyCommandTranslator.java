package com.zuunr.mongodb;

import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import org.bson.Document;

public class FindAndModifyCommandTranslator extends AbstractCommandTranslator {
    private final Json2BsonQueryTranslator queryTranslator = new Json2BsonQueryTranslator();

    public Document translate(JsonObject findAndModifyCommand) {
        Document translated = new Document();

        String collection = findAndModifyCommand.get("collection", JsonValue.NULL).getString();
        translated.put("findAndModify", collection); // collection name

        JsonObject query = findAndModifyCommand.get("query", JsonValue.NULL).getJsonObject();
        if (query != null) {
            translated.put("query", queryTranslator.translateQuery(query));
        }

        JsonValue update = findAndModifyCommand.get("update");
        if (update != null) {
            translated.put("update", translateDocuments(update));
        }

        JsonValue remove = findAndModifyCommand.get("remove");
        if (remove != null) {
            translated.put("remove", remove.getBoolean());
        }

        JsonValue _new = findAndModifyCommand.get("new");
        if (_new != null) {
            translated.put("new", _new.getBoolean());
        }
        return translated;
    }
}
