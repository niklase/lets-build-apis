package com.zuunr.dcentb.rest.requesthandler;

import com.zuunr.dcentb.rest.processor.Processor;
import com.zuunr.dcentb.rest.processor.accesscontrol.JwtSecretProvisioner;
import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import com.zuunr.json.JsonValueFactory;
import com.zuunr.jsontester.GivenWhenThenTesterBase;
import com.zuunr.mongodb.MongoJsonDB;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

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
 *
 * "given.config" (either an inline OpenAPI document or a classpath resource name, e.g.
 * "demo.openapi.json") is resolved here too. If that document has an "x-dcentb.jwtGeneration"
 * array, one self-issued JWT per entry is signed (using the same shared secret
 * JwtSecretProvisioner/SharedSecretJwtAuthenticator use) and exposed as a variable
 * named "{{bearer-jwt-" + entry.userId + "}}" with value "Bearer <token>" - available to every
 * when/then block in the test file exactly like any other "meta.variables" entry, so
 * a test can authenticate as e.g. "{{bearer-jwt-admin-a1}}" without hand-crafting a JWT.
 */
public abstract class DcentbGivenWhenThenTester extends GivenWhenThenTesterBase {

    protected JsonObject dbSetup;
    protected JsonObject rawConfig;
    protected JsonObject config;
    private JsonObject jwtVariables = JsonObject.EMPTY;

    @Override
    public void doGiven(JsonValue given) {
        dbSetup = given.get("dbSetup", JsonValue.NULL).getJsonObject();
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

        JsonValue configValue = given.get("config");
        rawConfig = configValue == null ? null : resolveConfig(configValue);
        config = rawConfig == null ? null : injectDbSetupIntoConfig(rawConfig);
        jwtVariables = config == null ? JsonObject.EMPTY : buildJwtVariables(config);
    }

    @Override
    protected JsonObject additionalVariables() {
        return jwtVariables;
    }

    protected JsonObject resolveConfig(JsonValue configValue) {
        String resourceName = configValue.getString();
        if (resourceName == null) {
            return configValue.getJsonObject();
        }
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            if (is == null) {
                throw new RuntimeException("Config resource not found on classpath: " + resourceName);
            }
            return JsonValueFactory.create(new String(is.readAllBytes())).getJsonObject();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load config resource: " + resourceName, e);
        }
    }

    private JsonObject buildJwtVariables(JsonObject config) {
        JsonObject tokensByUserId = new JwtSecretProvisioner().generateTokensByUserId(config);
        JsonObject variables = JsonObject.EMPTY;
        for (String userId : tokensByUserId.keySet()) {
            variables = variables.put("{{bearer-jwt-"+ userId + "}}", "Bearer " + tokensByUserId.get(userId).getString());
        }
        return variables;
    }

    protected JsonObject injectDbSetupIntoConfig(JsonObject config) {
        if (dbSetup == null) return config;
        JsonValue connection = dbSetup.get("connection");
        JsonValue db = dbSetup.get("db");

        return config
                .put(JsonArray.of(Processor.X_DCENTB, "mongodb", "connection"), connection)
                .put(JsonArray.of(Processor.X_DCENTB, "mongodb", "db"), db);

        /*
        JsonObject paths = config.get("paths", JsonObject.EMPTY).getJsonObject();
        JsonObject patchedPaths = paths;
        for (Map.Entry<String, JsonValue> pathEntry : paths.entrySet()) {
            JsonObject pathItem = pathEntry.getValue().getJsonObject();
            JsonObject patchedPathItem = pathItem;
            for (Map.Entry<String, JsonValue> methodEntry : pathItem.entrySet()) {
                JsonObject operation = methodEntry.getValue().getJsonObject();
                if (operation == null) continue;
                JsonValue xDcentb = operation.get("x-dcentb");
                if (xDcentb != null && xDcentb.get("mongodb") != null) {
                    JsonObject mongodb = xDcentb.get("mongodb").getJsonObject()
                            .put("connection", connection)
                            .put("db", db);
                    operation = operation.put("x-dcentb", xDcentb.getJsonObject().put("mongodb", mongodb));
                    patchedPathItem = patchedPathItem.put(methodEntry.getKey(), operation);
                }
            }
            patchedPaths = patchedPaths.put(pathEntry.getKey(), patchedPathItem);
        }
        return config.put("paths", patchedPaths);

         */
    }
}
