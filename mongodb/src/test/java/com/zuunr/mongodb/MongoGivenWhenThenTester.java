package com.zuunr.mongodb;

import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import com.zuunr.jsontester.GivenWhenThenTesterBase;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Base class for JSON-driven integration tests against MongoDB.
 *
 * Test files use a "tests" array where each element has exactly one field:
 * "given" (must be first), "when", or "then".  The "given" value must be
 * an array of MongoDB commands (e.g. drop, insert) that seed the database
 * before the "when" command is run.  Each given command must succeed
 * (ok == 1) or the test fails immediately.
 *
 * Example JSON test file structure:
 * <pre>
 * {
 *   "tests": [
 *     { "given": [
 *         { "drop":   { "collection": "persons" } },
 *         { "insert": { "collection": "persons", "documents": [{ "_id": "a-1", "name": "Alice" }] } }
 *       ]
 *     },
 *     { "when": { "find": { "collection": "persons", "filter": { ... } } } },
 *     { "then": { "ok": 1, "cursor": { "firstBatch": [{ "_id": "a-1", "name": "Alice" }] } } }
 *   ]
 * }
 * </pre>
 */
public abstract class MongoGivenWhenThenTester extends GivenWhenThenTesterBase {

    private JsonValue given;

    protected abstract MongoJsonDB getMongoJsonDB();

    @Override
    public void doGiven(JsonValue given) {
        this.given = given;
    }

    @Override
    public JsonValue doWhen(JsonValue when) {
        JsonArray commands = given.getJsonArray();
        for (int i = 0; i < commands.size(); i++) {
            JsonObject result = getMongoJsonDB().runCommand(commands.get(i).getJsonObject());
            assertEquals(JsonValue.of(1), result.get("ok"), "Given command #" + i + " failed: " + result);
        }
        return getMongoJsonDB().runCommand(when.getJsonObject()).jsonValue();
    }
}
