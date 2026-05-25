package com.zuunr.mongoschema;

import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import com.zuunr.jsontester.GivenWhenThenTesterBase;
import com.zuunr.mongodb.MongoJsonDB;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Integration tests for MongoSchemaGenerator.
 *
 * Each JSON file in the MongoSchemaGeneratorIT/ resource folder is run as a
 * separate parameterized test. The "given" value must be an array of MongoDB
 * commands that seed the database. The "when" value must be a JSON object with:
 *   - "collection" (required) – the collection to generate a schema for
 *   - "batchSize"  (optional, default 100) – documents per fetch
 *   - "threads"    (optional, default 1)   – concurrent fetch-and-generate threads
 */
class MongoSchemaGeneratorIT extends GivenWhenThenTesterBase {

    private static final JsonObject DB_CONFIG = JsonObject.EMPTY
            .put("connection", "mongodb://admin:adminpassword@localhost:27017/?authSource=admin")
            .put("db", "mongoschema");

    private static final MongoJsonDB MONGO_JSON_DB = new MongoJsonDB(DB_CONFIG);
    private static final MongoSchemaGenerator MONGO_SCHEMA_GENERATOR = new MongoSchemaGenerator(MONGO_JSON_DB);

    private JsonValue given;

    static Stream<Path> testFiles() throws Exception {
        return testFiles((Class<? extends GivenWhenThenTesterBase>) new Object() {}.getClass().getEnclosingClass()); // NOSONAR
    }

    @DisplayName("Run test for each JSON file")
    @ParameterizedTest(name = "{index} => JSON file: {0}")
    @MethodSource("testFiles")
    void test(Path testsFolderPath) throws Exception {
        executeTest(testsFolderPath);
    }

    @Override
    public void doGiven(JsonValue given) {
        this.given = given;
    }

    @Override
    public JsonValue doWhen(JsonValue when) {
        JsonArray commands = given.getJsonArray();
        for (int i = 0; i < commands.size(); i++) {
            JsonObject result = MONGO_JSON_DB.runCommand(commands.get(i).getJsonObject());
            assertEquals(JsonValue.of(1), result.get("ok"), "Given command #" + i + " failed: " + result);
        }

        String collectionName = when.get("collection").getString();
        int batchSize = when.get("batchSize", JsonValue.of(MongoSchemaGenerator.DEFAULT_BATCH_SIZE)).getInteger();
        int threads = when.get("threads", JsonValue.of(1)).getInteger();

        return MONGO_SCHEMA_GENERATOR.generateSchema(collectionName, null, batchSize, threads).jsonValue();
    }
}
