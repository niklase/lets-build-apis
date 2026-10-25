package com.zuunr.mongodb;

import com.zuunr.json.JsonObject;
import org.bson.Document;

public class Json2BsonTranslator {

    private final static JsonObjectTranslator<Object> jsonObjectTranslator = new JsonObjectTranslator<>();

    public final static Document translate(JsonObject jsonObject){
        return (Document) jsonObjectTranslator.translate(new Json2BsonStackItem(jsonObject));
    }

    /**
     * Same translation, but every leaf is converted according to {@code databaseTypedSchema}
     * instead of being inferred from the JSON value's own runtime type.
     */
    public final static Document translate(JsonObject jsonObject, DatabaseTypedSchema databaseTypedSchema) {
        return (Document) jsonObjectTranslator.translate(new TypedJson2BsonStackItem(jsonObject, databaseTypedSchema.rootNode()));
    }
}
