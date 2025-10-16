package com.zuunr.formbuilder;

import com.zuunr.json.JsonObject;

public interface Database {

    JsonObject createItem(String collectionName, JsonObject item);

    JsonObject readItem(String collectionName, String id);

    void writeItem(String collectionName, JsonObject item);

    void deleteItem(String collectionName, String id);
}
