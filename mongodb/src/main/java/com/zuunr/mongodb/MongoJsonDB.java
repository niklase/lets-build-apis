package com.zuunr.mongodb;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import com.zuunr.json.JsonValueFactory;
import com.zuunr.json.schema.JsonSchema;
import com.zuunr.json.schema.validation.JsonSchemaValidator;
import com.zuunr.json.schema.validation.OutputStructure;
import com.zuunr.json.util.JsonObjectWrapper;
import org.bson.Document;


public class MongoJsonDB implements AutoCloseable{

    private final ResourceDeserializer resourceDeserializer = new ResourceDeserializer().init();


    private final CreateCommandTranslator create = new CreateCommandTranslator();
    private final CreateIndexesCommandTranslator createIndexes = new CreateIndexesCommandTranslator();
    private final CreateUserCommandTranslator createUser = new CreateUserCommandTranslator();
    private final DropDatabaseCommandTranslator dropDatabase = new DropDatabaseCommandTranslator();
    private final AggregateCommandTranslator aggregate = new AggregateCommandTranslator();
    private final FindCommandTranslator find = new FindCommandTranslator();
    private final FindAndModifyCommandTranslator findAndModify = new FindAndModifyCommandTranslator();
    private final InsertCommandTranslator insert = new InsertCommandTranslator();
    private final UpdateCommandTranslator update = new UpdateCommandTranslator();
    private final DeleteCommandTranslator delete = new DeleteCommandTranslator();
    private final DropCommandTranslator drop = new DropCommandTranslator();

    private static final JsonSchema MONGODB_JSON_SCHEMA = JsonValueFactory.create(MongoJsonDB.class.getResourceAsStream("mongodb.schema.json")).as(JsonSchema.class);
    private static final JsonSchemaValidator JSON_SCHEMA_VALIDATOR = new JsonSchemaValidator();

    private MongoClient mongoClient;
    private MongoDatabase mongoDatabase;

    public MongoJsonDB(JsonValue mongoConfig) {
        this(mongoConfig.getJsonObject());
    }

    public MongoJsonDB(JsonObject config) {
        this(
                config.get("connection").getString(),
                config.get("db").getString());
    }

    public JsonObject validateCommand(JsonObject command) {
        return JSON_SCHEMA_VALIDATOR.validate(command.jsonValue(), MONGODB_JSON_SCHEMA, OutputStructure.DETAILED);
    }

    public MongoJsonDB(String connectionString, String databaseName) {

        // Create a MongoClient instance
        try {
            mongoClient = MongoClients.create(connectionString);
            // Access a specific database
            mongoDatabase = mongoClient.getDatabase(databaseName);
            System.out.println("mongodb ping result: " + mongoDatabase.runCommand(new Document("ping", 1)));
            // Example: Print the database name
            System.out.println("Connected to database: " + mongoDatabase.getName());

        } catch (Exception e) {
            System.err.println("An error occurred: " + e.getMessage());
        }
    }

    public MongoJsonDB(MongoDatabase mongoDatabase) {
        this.mongoDatabase = mongoDatabase;
    }

    public JsonObject runCommand(JsonObject command) {

        MONGODB_JSON_SCHEMA.validate();
        JsonObject validationResult = JSON_SCHEMA_VALIDATOR.validate(command.jsonValue(), MONGODB_JSON_SCHEMA, OutputStructure.DETAILED);
        if (!validationResult.get("valid").getBoolean()) {
            throw new RuntimeException("Invalid mongodb command: " + validationResult);
        }

        Document bsonCommand = translate(command);
        return runCommand(bsonCommand, mongoDatabase);
    }

    private JsonObject runCommand(Document bsonCommand, MongoDatabase mongoDatabase) {

        Document outDoc = mongoDatabase.runCommand(bsonCommand);
        return resourceDeserializer.deserialize(outDoc, JsonObjectWrapper.class).asJsonObject();
    }


    private Document translate(JsonObject command) {

        String commandName = command.keys().get(0).getString();
        JsonObject commandValue = command.values().get(0).getJsonObject();
        //System.out.println("db command: "+commandName+"\n" + commandValue.asPrettyJson());
        switch (commandName) {
            case "aggregate":
                return aggregate.translate(commandValue);
            case "create":
                return create.translate(commandValue);
            case "createIndexes":
                return createIndexes.translate(commandValue);
            case "createUser":
                return createUser.translate(commandValue);
            case "dropDatabase":
                return dropDatabase.translate(commandValue);
            case "find":
                return find.translate(commandValue);
            case "findAndModify":
                return findAndModify.translate(commandValue);
            case "insert":
                return insert.translate(commandValue);
            case "update":
                return update.translate(commandValue);
            case "delete":
                return delete.translate(commandValue);
            case "drop":
                return drop.translate(commandValue);
            default:
                throw new RuntimeException("Unsupported command");
        }
    }

    @Override
    public void close() throws Exception {
        if (mongoClient != null) {
            mongoClient.close();
        }
    }
}