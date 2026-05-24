package com.zuunr.dcentb.rest.requesthandler;

import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import com.zuunr.jsontester.GivenWhenThenTesterBase;
import com.zuunr.mongodb.MongoJsonDB;

/**
 * Base class for dcentb request-handler integration tests.
 *
 * If the "given" object contains a "dbSetup" key, the specified MongoDB
 * commands (drop, insert, …) are executed against the configured database
 * before the HTTP request under test is processed.  This mirrors the pattern
 * used by MongoGivenWhenThenTester and allows test files to be fully
 * self-contained.
 *
 * "given" structure when database setup is needed:
 * <pre>
 * {
 *   "dbSetup": {
 *     "connection": "mongodb://...",
 *     "db": "mydb",
 *     "commands": [
 *       { "drop":   { "collection": "players" } },
 *       { "insert": { "collection": "players", "documents": [ ... ] } }
 *     ]
 *   },
 *   "openapi": "3.1.0",
 *   "paths": { ... }
 * }
 * </pre>
 */
public abstract class DcentbGivenWhenThenTester extends GivenWhenThenTesterBase {

    @Override
    public final JsonValue doGivenWhen(JsonValue given, JsonValue when) {
        JsonObject dbSetup = given.get("dbSetup", JsonValue.NULL).getJsonObject();
        if (dbSetup != null) {
            JsonObject mongoConfig = JsonObject.EMPTY
                    .put("connection", dbSetup.get("connection"))
                    .put("db", dbSetup.get("db"));
            MongoJsonDB mongoDB = new MongoJsonDB(mongoConfig);
            JsonArray commands = dbSetup.get("commands").getJsonArray();
            for (int i = 0; i < commands.size(); i++) {
                mongoDB.runCommand(commands.get(i).getJsonObject());
            }
        }
        return doWhen(given, when);
    }

    protected abstract JsonValue doWhen(JsonValue given, JsonValue when);
}
