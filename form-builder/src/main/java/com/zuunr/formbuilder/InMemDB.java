package com.zuunr.formbuilder;

import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;

import java.util.UUID;

public class InMemDB implements Database {

    JsonObject store = JsonObject.EMPTY;

    public JsonObject createItem(String collectionName, JsonObject item) {
        String id = UUID.randomUUID().toString();
        item = item.put(RestMachine.ITEM_ID_POINTER, id);
        store = store.put(id, item);
        return item;
    }


    public JsonObject readItem(String collectionName, String id) {
        return store.get(id, JsonValue.NULL).getJsonObject();
    }


    public void writeItem(String collectionName, JsonObject item) {
        store = store.put(item.get(RestMachine.ITEM_ID_POINTER).getString(), item);
    }

    @Override
    public void deleteItem(String collectionName, String id) {
        store = store.remove(id);
    }
}
