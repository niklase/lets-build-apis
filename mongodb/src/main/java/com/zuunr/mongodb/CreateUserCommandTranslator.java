package com.zuunr.mongodb;

import com.zuunr.json.JsonObject;
import org.bson.Document;

public class CreateUserCommandTranslator extends AbstractCommandTranslator {

    public Document translate(JsonObject command) {
        Document document = new Document();
        document.put("createUser", command.get("user").getString());
        document.put("pwd", command.get("pwd").getString());
        document.put("roles", Json2BsonTranslator.translate(JsonObject.EMPTY.put("roles", command.get("roles"))).get("roles"));
        return document;
    }
}
