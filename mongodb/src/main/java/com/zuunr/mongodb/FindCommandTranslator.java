package com.zuunr.mongodb;

import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import org.bson.Document;

public class FindCommandTranslator {

    private final Json2BsonQueryTranslator queryTranslator = new Json2BsonQueryTranslator();
    private final Json2BsonSortTranslator sortTranslator = new Json2BsonSortTranslator();

    public Document translate(JsonObject findCommand) {
        Document translated = new Document();

        String collection = findCommand.get("collection", JsonValue.NULL).getString();
        if (collection == null) {
            collection = findCommand.get("find").getString();
        }
        translated.put("find", collection); // collection name

        JsonObject filter = findCommand.get("filter", JsonValue.NULL).getJsonObject();
        if (filter != null) {
            translated.put("filter", queryTranslator.translateQuery(filter));
        }

        Integer skip = findCommand.get("skip", JsonValue.NULL).getInteger();
        if (skip != null){
            translated.put("skip", skip);
        }

        Integer limit = findCommand.get("limit", JsonValue.NULL).getInteger();
        if (limit != null){
            translated.put("limit", limit);
        }

        JsonArray sort = findCommand.get("sort", JsonValue.NULL).getJsonArray();
        if (sort != null){
            translated.put("sort", sortTranslator.translate(sort));
        }

        return translated;
    }
}
