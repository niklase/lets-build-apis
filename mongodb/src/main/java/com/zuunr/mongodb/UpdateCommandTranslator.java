package com.zuunr.mongodb;

import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;

public class UpdateCommandTranslator {

    private final Json2BsonQueryTranslator queryTranslator;
    private final CollectionSchemas collectionSchemas;

    public UpdateCommandTranslator(CollectionSchemas collectionSchemas) {
        this.collectionSchemas = collectionSchemas;
        this.queryTranslator = new Json2BsonQueryTranslator(collectionSchemas);
    }

    public Document translate(JsonObject updateCommand) {
        Document translated = new Document();

        String collection = updateCommand.get("collection", JsonValue.NULL).getString();
        if (collection == null) {
            collection = updateCommand.get("update").getString();
        }
        translated.put("update", collection); // collection name

        JsonArray updates = updateCommand.get("updates").getJsonArray();
        List updatesTranslated = translateUpdates(collection, updates);
        translated.put("updates", updatesTranslated);
        return translated;
    }

    private List<Object> translateUpdates(String collection, JsonArray updates) {
        ArrayList<Object> list = new ArrayList<>();
        for (int i = 0; i < updates.size(); i++) {

            JsonObject updateStatement = updates.get(i).getJsonObject();
            list.add(translateUpdateStatement(collection, updateStatement));
        }
        return list;
    }

    private Document translateUpdateStatement(String collection, JsonObject updateStatement) {

        Document updateStatementDoc = new Document();

        Document q = queryTranslator.translateQuery(collection, updateStatement.get("q").getJsonObject());
        updateStatementDoc.put("q", q);

        Document u = collectionSchemas.translateDocument(collection, updateStatement.get("u").getJsonObject());

        updateStatementDoc.put("u", u);

        if (updateStatement.get("upsert", false).getBoolean()) {
            updateStatementDoc.put("upsert", true);
        }
        return updateStatementDoc;
    }
}
