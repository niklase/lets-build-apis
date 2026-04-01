package com.zuunr.mongodb;

import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import org.bson.Document;

public class Json2BsonSortTranslator {

    /**
     * @param sort e.g <code>[{"age": -1},{"name.last": 1}]</code> or {"$and": [{"age": [{"$gt": 18}]}]}
     * @return
     */
    public Document translate(JsonArray sort) {

        Document document = new Document();
        for (int i = 0; i < sort.size(); i++) {
            JsonObject element = sort.get(i).getJsonObject();
            document.put(element.keys().head().getString(), element.values().head().getInteger());
        }
        return document;
    }


}
