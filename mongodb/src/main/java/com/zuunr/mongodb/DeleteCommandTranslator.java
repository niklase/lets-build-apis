package com.zuunr.mongodb;

import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;

public class DeleteCommandTranslator {

    private final Json2BsonQueryTranslator queryTranslator = new Json2BsonQueryTranslator();

    public Document translate(JsonObject deleteCommand) {

        Document translated = new Document();

        String collection = deleteCommand.get("collection", JsonValue.NULL).getString();
        translated.put("delete", collection); // collection name

        JsonArray deletesJson = deleteCommand.get("deletes").getJsonArray();
        List<Document> deletesTranslated = new ArrayList<>(deletesJson.size());

        for (int i = 0; i < deletesJson.size(); i++) {
            deletesTranslated.add(translateDeletesItem(deletesJson.get(i).getJsonObject()));
        }
        translated.put("deletes", deletesTranslated);

        /*
         Json:
         {"delete":{
            "collection": "persons",
            "deletes": [
                {
                    "q": query,
                    "limit": 0/1
                }
            ]
         }}


         db.runCommand(
            {
              delete: <collection>,
              deletes: [
                 {
                   q : <query>,
                   limit : <integer>,
                   collation: <document>,
                   hint: <document|string>
                 },
                 ...
              ],
              comment: <any>,
              let: <document>, // Added in MongoDB 5.0
              ordered: <boolean>,
              writeConcern: { <write concern> }
           }
        )
        */
        return translated;
    }

    private Document translateDeletesItem(JsonObject deletesItem) {
        Document translated = new Document();
        translated.put("q", queryTranslator.translateQuery(deletesItem.get("q").getJsonObject()));
        translated.put("limit", deletesItem.get("limit").getJsonNumber().intValue());
        return translated;
    }
}
