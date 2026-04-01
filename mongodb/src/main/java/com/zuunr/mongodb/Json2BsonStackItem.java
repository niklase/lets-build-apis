package com.zuunr.mongodb;

import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;

public class Json2BsonStackItem extends JsonTranslatorStackItem<Object> {


    public Json2BsonStackItem(JsonObject jsonObject) {
        super(jsonObject.jsonValue(), null, null);
    }

    public Json2BsonStackItem(JsonValue jsonValue, Json2BsonStackItem parent, String parentKey) {
        super(jsonValue, parent, parentKey);
    }


    @Override
    public void addTranslatedToParent(Object value) {
        if (parent == null) {
            return;
        }

        if (parent.jsonValue.isJsonObject()) {
            if (parent.getTranslated() == null) {
                parent.setTranslated(new Document());
            }
            ((Document) parent.getTranslated()).put(parentKey, value);
        }

        if (parent.jsonValue.isJsonArray()) {
            if (parent.getTranslated() == null) {
                parent.setTranslated(new ArrayList(parent.jsonValue.getJsonArray().size())); // init size may not be correct at all times but should be a good initial value
            }
            ((List) parent.getTranslated()).add(value);
        }
    }

    @Override
    public boolean translateObject() {
        JsonObject jsonObject = jsonValue.getJsonObject();
        if (jsonObject.isEmpty()) {
            setTranslated(new Document());
        } else if (jsonObject.size() == 1) {
            JsonValue objectId = jsonObject.get("ObjectId");
            if (objectId != null && objectId.isString()) {
                MongoObjectId mongoObjectId = objectId.as(MongoObjectId.class);
                if (mongoObjectId.isValid()) {
                    setTranslated(mongoObjectId.getObjectId());
                } else {
                    // setTranslated(translateString());
                }
            }
        }
        return getTranslated() != null;
    }

    @Override
    public boolean translateArray() {
        JsonArray topJsonArray = jsonValue.getJsonArray();
        if (topJsonArray.isEmpty()) {
            setTranslated(new ArrayList(0));
        }
        return getTranslated() != null;
    }

    @Override
    public boolean translateLeaf() {
        JsonValue value = jsonValue;
        if (value.isJsonNumber()) {
            setTranslated(value.getJsonNumber().getWrappedNumber());
            return true;
        } else if (value.isString()) {
            setTranslated(translateString());
            return true;
        } else {
            setTranslated(value.getValue());
            return true;
        }
    }

    private Object translateString() {
        return jsonValue.getString();
    }

    @Override
    public JsonTranslatorStackItem<Object> createChild(JsonValue toBeTranslated, String parentKey) {
        return new Json2BsonStackItem(toBeTranslated, this, parentKey);
    }
}

