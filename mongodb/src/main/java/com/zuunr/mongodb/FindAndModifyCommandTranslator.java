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
            // A single modifier document (e.g. {"$set": {...}, "$inc": {...}}), not a list — findAndModify's
            // "update" field takes one document, unlike the bulk "update" command's per-statement "u".
            translated.put("update", Json2BsonTranslator.translate(update.getJsonObject()));
        }

        JsonValue upsert = findAndModifyCommand.get("upsert");
        if (upsert != null) {
            translated.put("upsert", upsert.getBoolean());
        }

        JsonValue remove = findAndModifyCommand.get("remove");
        if (remove != null) {
            translated.put("remove", remove.getBoolean());
        }

        JsonValue _new = findAndModifyCommand.get("new");
        if (_new != null) {
            translated.put("new", _new.getBoolean());
        }

        JsonObject writeConcern = findAndModifyCommand.get("writeConcern", JsonValue.NULL).getJsonObject();
        if (writeConcern != null) {
            translated.put("writeConcern", Json2BsonTranslator.translate(writeConcern));
        }

        JsonObject readConcern = findAndModifyCommand.get("readConcern", JsonValue.NULL).getJsonObject();
        if (readConcern != null) {
            translated.put("readConcern", Json2BsonTranslator.translate(readConcern));
        }
        return translated;
    }
}
