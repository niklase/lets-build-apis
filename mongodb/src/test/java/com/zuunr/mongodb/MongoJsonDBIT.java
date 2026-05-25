package com.zuunr.mongodb;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import com.zuunr.jsontester.GivenWhenThenTesterBase;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MongoJsonDBIT extends GivenWhenThenTesterBase {

    private static JsonObject dbConfig = JsonObject.EMPTY
            .put("connection", "mongodb://admin:adminpassword@localhost:27017/?authSource=admin")
            .put("db", "MongoJsonDBIT".toLowerCase());

    private static MongoClient mongoClient = MongoClients.create(dbConfig.get("connection").getString());
    // Step 1: Connect to the database
    private static MongoDatabase database = mongoClient.getDatabase(dbConfig.get("db").getString());

    private static MongoJsonDB mongoJsonDB = new MongoJsonDB(database);

    private JsonValue given;

    /*
     * This method implementation may be copied as-is to any other subclass of GivenWhenThenBaseTester
     */
    static Stream<Path> testFiles() throws Exception {
        return testFiles((Class<? extends GivenWhenThenTesterBase>) new Object() {
        }.getClass().getEnclosingClass()); // NOSONAR
    }

    /*
     * This method implementation and annotations may be copied as-is to any other subclass of GivenWhenThenBaseTester
     */
    @DisplayName("Run test for each JSON file")
    @ParameterizedTest(name = "{index} => JSON file: {0}")
    @MethodSource("testFiles")
    void test(Path testsFolderPath) throws Exception {
        executeTest(testsFolderPath);
    }

    @Test
    void insert1() {
        JsonObject result = mongoJsonDB.runCommand(
                JsonObject.EMPTY
                        .put("insert", JsonObject.EMPTY
                                .put("collection", "mytestcollection")
                                .put("documents", JsonArray.of(JsonObject.EMPTY
                                        .put("name", "Peter Andersson")))));
        assertEquals(JsonValue.of(1), result.get("ok"));
        assertEquals(JsonValue.of(1), result.get("n"));
    }

    @Test
    void insertAndFind1() {
        String secretId = UUID.randomUUID().toString();
        JsonObject command = JsonObject.EMPTY
                .put("insert", JsonObject.EMPTY
                        .put("collection", "persons")
                        .put("documents", JsonArray.of(
                                JsonObject.EMPTY
                                        .put("name", "Peter Andersson")
                                        .put("secretId", secretId))));

        JsonObject validationResult = mongoJsonDB.validateCommand(command);
        assertEquals(JsonValue.TRUE, validationResult.get("valid"));
        JsonObject result = mongoJsonDB.runCommand(command);
        assertEquals(JsonValue.of(1), result.get("ok"));
        assertEquals(1L, result.get("n").getJsonNumber().getWrappedNumber());

        JsonObject findCommand = JsonObject.EMPTY
                .put("find", JsonObject.EMPTY
                        .put("collection", "persons")
                        .put("filter", JsonObject.EMPTY
                                .put("$and", JsonArray.of(
                                        JsonObject.EMPTY
                                                .put("name", JsonArray.of(
                                                        JsonObject.EMPTY.put("$eq", "Peter Andersson"))
                                                ),
                                        JsonObject.EMPTY
                                                .put("secretId", JsonArray.of(
                                                        JsonObject.EMPTY.put("$eq", secretId))
                                                )
                                ))));

        JsonObject validationresultOfFindCommand = mongoJsonDB.validateCommand(findCommand);
        assertEquals(JsonValue.TRUE, validationresultOfFindCommand.get("valid"));
        JsonObject findResult = mongoJsonDB.runCommand(findCommand);
        assertEquals(JsonValue.of(1), findResult.get("ok"));
        assertEquals(1, findResult.get(JsonArray.of("cursor", "firstBatch")).getJsonArray().size());
    }

    @Test
    void insertUpdateAndFind1() {

        String secretId = UUID.randomUUID().toString();
        JsonObject resultOfInsert = mongoJsonDB.runCommand(
                JsonObject.EMPTY
                        .put("insert", JsonObject.EMPTY
                                .put("collection", "persons")
                                .put("documents", JsonArray.of(JsonObject.EMPTY
                                        .put("name", "Peter Andersson")
                                        .put("secretId", secretId)))));
        assertEquals(JsonValue.of(1), resultOfInsert.get("ok"));
        assertEquals(1, resultOfInsert.get("n").getInteger());

        JsonObject resultOfUpdate = mongoJsonDB.runCommand(JsonObject.EMPTY
                .put("update", JsonObject.EMPTY
                        .put("collection", "persons")
                        .put("updates", JsonArray.of(JsonObject.EMPTY
                                .put("q", JsonObject.EMPTY
                                        .put("secretId", JsonArray.of(
                                                JsonObject.EMPTY.put("$eq", secretId))
                                        ))
                                .put("u",
                                        JsonObject.EMPTY
                                                .put("name", "Peter Andersson")
                                                .put("updated", true)
                                                .put("secretId", secretId))))));

        assertEquals(JsonValue.of(1), resultOfUpdate.get("ok"));
        assertEquals(1L, resultOfUpdate.get("n").getJsonNumber().getWrappedNumber());

        JsonObject findCommand = JsonObject.EMPTY
                .put("find", JsonObject.EMPTY
                        .put("collection", "persons")
                        .put("filter", JsonObject.EMPTY
                                .put("$and", JsonArray.of(
                                        JsonObject.EMPTY
                                                .put("updated", JsonArray.of(
                                                        JsonObject.EMPTY.put("$eq", true))
                                                ),
                                        JsonObject.EMPTY
                                                .put("secretId", JsonArray.of(
                                                        JsonObject.EMPTY.put("$eq", secretId))
                                                )
                                ))));

        JsonObject resultOfFind = mongoJsonDB.runCommand(findCommand);
        assertEquals(JsonValue.of(1), resultOfFind.get("ok"));
        assertEquals(1, resultOfFind.get(JsonArray.of("cursor", "firstBatch")).getJsonArray().size());
    }

    @Test
    void endpointHandlers() {

        String objectId = new ObjectId().toHexString();
        JsonObject resultOfInsert = mongoJsonDB.runCommand(
                JsonObject.EMPTY
                        .put("insert", JsonObject.EMPTY
                                .put("collection", "endpoint-handlers")
                                .put("documents", JsonArray.of(JsonObject.EMPTY
                                        .put("_id", JsonObject.EMPTY.put("ObjectId", objectId))
                                        .put("path", "/project1/persons/*")
                                        .put("method", "get")
                                        .put("processors", JsonObject.EMPTY
                                                .put("onRequest", JsonObject.EMPTY
                                                        .put("JsonSchemaValidator", JsonObject.EMPTY)))

                                ))));
        assertEquals(JsonValue.of(1), resultOfInsert.get("ok"));
        assertEquals(1L, resultOfInsert.get("n").getJsonNumber().getWrappedNumber());

        JsonObject findCommand = JsonObject.EMPTY
                .put("find", JsonObject.EMPTY
                        .put("collection", "endpoint-handlers")
                        .put("filter", JsonObject.EMPTY
                                .put("_id", JsonArray.of(JsonObject.EMPTY
                                        .put("$eq", JsonObject.EMPTY.put("ObjectId", objectId))))));
        JsonObject resultOfFindValidation = mongoJsonDB.validateCommand(findCommand);
        assertEquals(JsonValue.TRUE, resultOfFindValidation.get("valid"));
        JsonObject resultOfFind = mongoJsonDB.runCommand(findCommand);
        assertEquals(JsonValue.of(1), resultOfFind.get("ok"));
        assertEquals(1, resultOfFind.get(JsonArray.of("cursor", "firstBatch")).getJsonArray().size());
    }

    @Override
    public void doGiven(JsonValue given){
        this.given = given;
    }

    @Override
    public JsonValue doWhen(JsonValue when) {

        JsonArray jsonArray = given.getJsonArray();
        for (int i = 0; i < jsonArray.size(); i++) {
            JsonObject command = jsonArray.get(i).getJsonObject();
            JsonObject commandResult = mongoJsonDB.runCommand(command);
            assertEquals(JsonValue.of(1), commandResult.get("ok"));
        }
        return mongoJsonDB.runCommand(when.getJsonObject()).jsonValue();
    }

}