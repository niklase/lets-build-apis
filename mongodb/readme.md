# A JSON API for MongoDB

This is a JSON wrapper for the MongoDB java driver (sync).

## BSON is not JSON

BSON preserves order between fields in objects
JSON has no order between fileds in objects

To create BSON from JSON we use JSON arrays when order is important.


# Create a MongoJsonDB client

    MongoClient mongoClient = MongoClients.create("mongodb://admin:adminpassword@localhost:27017/?authSource=admin");
    
    MongoDatabase database = mongoClient.getDatabase("myDatabase");

    MongoJsonDB mongoJsonDB = new MongoJsonDB(database);

# Insert documents into database

    String commandStr = """
        {
          "insert": {
            "collection": "persons",
            "documents": [
              {
                "name": "Peter"
              },
              {
                "name": "Laura"
              }
            ]
          }
        }
        """;

    JsonObject command = JsonValueFactory.create(commandStr).getJsonObject();
    
    JsonObject result = mongoJsonDB.runCommand(command);
    
# More examples...

See tests in <code>./src/test/resources/com/zuunr/mongodb/MongoJsonClientIT</code>

