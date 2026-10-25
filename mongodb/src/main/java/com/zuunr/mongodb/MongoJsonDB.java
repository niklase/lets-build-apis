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
    private final DropCommandTranslator drop = new DropCommandTranslator();

    // These translate a collection's own documents or query filters (insert's "documents",
    // update's "q"/"u", findAndModify's "query"/"update", find's "filter", delete's "q",
    // aggregate's "$match" stages), so - unlike the translators above - they need the
    // collection's registered schema and are built once that schema registry exists, in
    // initCollectionSchemas().
    private CollectionSchemas collectionSchemas;
    private AggregateCommandTranslator aggregateCommandTranslator;
    private FindCommandTranslator findCommandTranslator;
    private FindAndModifyCommandTranslator findAndModifyCommandTranslator;
    private InsertCommandTranslator insertCommandTranslator;
    private UpdateCommandTranslator updateCommandTranslator;
    private DeleteCommandTranslator deleteCommandTranslator;

    private static final JsonSchema MONGODB_JSON_SCHEMA = JsonValueFactory.create(MongoJsonDB.class.getResourceAsStream("mongodb.schema.json")).as(JsonSchema.class);
    private static final JsonSchemaValidator JSON_SCHEMA_VALIDATOR = new JsonSchemaValidator();

    private MongoClient mongoClient;
    private MongoDatabase mongoDatabase;

    public MongoJsonDB(JsonValue mongoConfig) {
        this(mongoConfig.getJsonObject());
    }

    /**
     * @param config may also carry "collectionSchemas" - see {@link CollectionSchemas}.
     */
    public MongoJsonDB(JsonObject config) {
        this(
                config.get("connection").getString(),
                config.get("db").getString(),
                config);
    }

    public JsonObject validateCommand(JsonObject command) {
        return JSON_SCHEMA_VALIDATOR.validate(command.jsonValue(), MONGODB_JSON_SCHEMA, OutputStructure.DETAILED);
    }

    public MongoJsonDB(String connectionString, String databaseName) {
        this(connectionString, databaseName, JsonObject.EMPTY);
    }

    /**
     * @param config carries "collectionSchemas" - see {@link CollectionSchemas}. "connection" and
     *               "db" are ignored here; they're only read by {@link #MongoJsonDB(JsonObject)}.
     */
    public MongoJsonDB(String connectionString, String databaseName, JsonObject config) {
        initCollectionSchemas(config);

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
        this(mongoDatabase, JsonObject.EMPTY);
    }

    /**
     * @param config carries "collectionSchemas" - see {@link CollectionSchemas}.
     */
    public MongoJsonDB(MongoDatabase mongoDatabase, JsonObject config) {
        this.mongoDatabase = mongoDatabase;
        initCollectionSchemas(config);
    }

    private void initCollectionSchemas(JsonObject config) {
        this.collectionSchemas = new CollectionSchemas(config);
        this.aggregateCommandTranslator = new AggregateCommandTranslator(collectionSchemas);
        this.findCommandTranslator = new FindCommandTranslator(collectionSchemas);
        this.findAndModifyCommandTranslator = new FindAndModifyCommandTranslator(collectionSchemas);
        this.insertCommandTranslator = new InsertCommandTranslator(collectionSchemas);
        this.updateCommandTranslator = new UpdateCommandTranslator(collectionSchemas);
        this.deleteCommandTranslator = new DeleteCommandTranslator(collectionSchemas);
    }

    /**
     * Converts a raw BSON {@link Document} obtained outside the {@link #runCommand} request/response
     * flow — e.g. from a change stream event, which is a long-lived tailable cursor with no
     * command/response shape a JSON command could represent — into a {@link JsonObject}, using the
     * same deserialization this class already applies to every {@code runCommand} result.
     */
    public JsonObject toJsonObject(Document document) {
        return resourceDeserializer.deserialize(document, JsonObjectWrapper.class).asJsonObject();
    }

    /**
     * Lightweight liveness/readiness probe: issues the Mongo {@code ping} admin command directly
     * against the driver, bypassing the JSON-command validation/translation {@link #runCommand}
     * uses - {@code ping} isn't one of the CRUD-shaped commands {@link #translate} models, and would
     * throw "Unsupported command" if attempted through it. Throws whatever the driver throws on
     * failure; callers decide how to turn that into a health status. Unrelated to, and does not
     * change, the one-time ping already done in the (connectionString, databaseName) constructor,
     * which logs to System.out/System.err and swallows everything - that path stays as-is.
     */
    public void ping() {
        mongoDatabase.runCommand(new Document("ping", 1));
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
                return aggregateCommandTranslator.translate(commandValue);
            case "create":
                return create.translate(commandValue);
            case "createIndexes":
                return createIndexes.translate(commandValue);
            case "createUser":
                return createUser.translate(commandValue);
            case "dropDatabase":
                return dropDatabase.translate(commandValue);
            case "find":
                return findCommandTranslator.translate(commandValue);
            case "findAndModify":
                return findAndModifyCommandTranslator.translate(commandValue);
            case "insert":
                return insertCommandTranslator.translate(commandValue);
            case "update":
                return updateCommandTranslator.translate(commandValue);
            case "delete":
                return deleteCommandTranslator.translate(commandValue);
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