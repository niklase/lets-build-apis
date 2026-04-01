package com.zuunr.mongodb;

import com.zuunr.json.JsonObject;
import org.bson.Document;

public class Json2BsonTranslator {

    private final static JsonObjectTranslator<Object> jsonObjectTranslator = new JsonObjectTranslator<>();

    public final static Document translate(JsonObject jsonObject){
        return (Document) jsonObjectTranslator.translate(new Json2BsonStackItem(jsonObject));
    }
}
