package com.example.test;

import com.zuunr.json.JsonObject;
import com.zuunr.jsontester.GivenWhenThenTesterBase;
import com.zuunr.mongodb.MongoGivenWhenThenTester;
import com.zuunr.mongodb.MongoJsonDB;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Example integration test demonstrating MongoGivenWhenThenTester.
 *
 * Each JSON file in the PersonQueryExampleIT/ resource folder is run as a
 * separate parameterized test case.  Each file uses a "tests" array where
 * the elements (in order) carry exactly one of "given", "when", or "then".
 */
class PersonQueryExampleIT extends MongoGivenWhenThenTester {

    private static final JsonObject DB_CONFIG = JsonObject.EMPTY
            .put("connection", "mongodb://admin:adminpassword@localhost:27017/?authSource=admin")
            .put("db", "personqueryexample");

    private static final MongoJsonDB MONGO_JSON_DB = new MongoJsonDB(DB_CONFIG);

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
    protected MongoJsonDB getMongoJsonDB() {
        return MONGO_JSON_DB;
    }
}
