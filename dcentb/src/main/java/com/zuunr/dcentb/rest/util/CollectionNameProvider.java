package com.zuunr.dcentb.rest.util;

import com.zuunr.json.JsonObject;

public class CollectionNameProvider {

    private static final String GET_COLLECTION = "/getCollection";
    private static final String ITEM_ID = "/{id}";
    public static String getCollectionName(JsonObject operation) {
        String path = operation.get("path").getString();
        String collectionName = path.substring(1);

        String modified =  collectionName.replaceFirst("[/]((getCollection)|([{][^}]+[}]))$", "");

        return modified;
    }



}
