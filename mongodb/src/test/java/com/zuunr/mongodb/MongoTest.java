package com.zuunr.mongodb;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;

public class MongoTest {
    public static void main(String[] args) {
        // Replace this with your own connection string, if needed
        String connectionString = "mongodb://admin:adminpassword@localhost:27017/?authSource=admin";

        // Database and collection names
        String databaseName = "testDatabase";
        String collectionName = "testvartestar";

        try (MongoClient mongoClient = MongoClients.create(connectionString)) {
            // Step 1: Connect to the database
            MongoDatabase database = mongoClient.getDatabase(databaseName);
            System.out.println("Connected to database: " + databaseName);

            // Step 2: Get or create the collection
            MongoCollection<Document> collection = database.getCollection(collectionName);
            System.out.println("Collection accessed: " + collectionName);

            // Step 3: Create a document
            Document document = new Document("name", "TestUser")
                    .append("age", 30)
                    .append("location", "Earth");
            System.out.println("Document created: " + document.toJson());

            // Step 4: Insert the document
            collection.insertOne(document);
            System.out.println("Document inserted into collection: " + collectionName);

            // Step 5: Verify by reading the document back
            System.out.println("Data in the collection:");
            collection.find().forEach(doc -> System.out.println(doc.toJson()));
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Failed to complete the test.");
        }
    }
}