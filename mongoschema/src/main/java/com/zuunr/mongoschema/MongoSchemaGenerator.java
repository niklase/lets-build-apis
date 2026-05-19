package com.zuunr.mongoschema;

import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import com.zuunr.json.schema.generation.BasicSchemaUnifier;
import com.zuunr.json.schema.generation.SchemaGenerator;
import com.zuunr.json.schema.generation.SchemaUnifier;
import com.zuunr.mongodb.MongoJsonDB;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

public class MongoSchemaGenerator {

    public static final int DEFAULT_BATCH_SIZE = 100;

    private final MongoJsonDB mongoJsonDB;
    private final SchemaGenerator schemaGenerator;
    private final SchemaUnifier schemaUnifier;

    public MongoSchemaGenerator(MongoJsonDB mongoJsonDB) {
        this(mongoJsonDB, new SchemaGenerator(), new BasicSchemaUnifier());
    }

    public MongoSchemaGenerator(MongoJsonDB mongoJsonDB, SchemaGenerator schemaGenerator, SchemaUnifier schemaUnifier) {
        this.mongoJsonDB = mongoJsonDB;
        this.schemaGenerator = schemaGenerator;
        this.schemaUnifier = schemaUnifier;
    }

    public JsonObject generateSchema(String collectionName) {
        return generateSchema(collectionName, null, DEFAULT_BATCH_SIZE, 1);
    }

    public JsonObject generateSchema(String collectionName, int batchSize, int threads) {
        return generateSchema(collectionName, null, batchSize, threads);
    }

    /**
     * Iterates all documents in the collection (or the filtered subset) in batches,
     * generating a partial JSON Schema per batch and merging the results.
     *
     * Each of the {@code threads} threads independently claims the next unclaimed
     * batch via a shared atomic offset, then fetches that batch from MongoDB and
     * generates its schema locally before claiming the next one. Thread-local
     * partial schemas are merged with {@link SchemaUnifier} after all threads
     * finish.
     *
     * @param collectionName name of the MongoDB collection
     * @param filter         optional MongoDB query filter; null means match all
     * @param batchSize      number of documents per fetch (skip/limit pagination)
     * @param threads        number of concurrent fetch-and-generate threads
     */
    public JsonObject generateSchema(String collectionName, JsonObject filter, int batchSize, int threads) {
        AtomicInteger offset = new AtomicInteger(0);

        List<Callable<JsonObject>> tasks = new ArrayList<>(threads);
        for (int i = 0; i < threads; i++) {
            tasks.add(() -> fetchAndGenerate(collectionName, filter, batchSize, offset));
        }

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Future<JsonObject>> futures = executor.invokeAll(tasks);
            JsonObject merged = null;
            for (Future<JsonObject> future : futures) {
                JsonObject threadSchema = future.get();
                if (threadSchema != null && !threadSchema.isEmpty()) {
                    merged = merged == null ? threadSchema : merge(merged, threadSchema);
                }
            }
            return merged != null ? merged : JsonObject.EMPTY;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Schema generation interrupted", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Schema generation failed", e.getCause());
        } finally {
            executor.shutdown();
        }
    }

    /**
     * Repeatedly claims the next batch offset, fetches the batch, and merges its
     * schema into a thread-local accumulator until the collection is exhausted.
     */
    private JsonObject fetchAndGenerate(String collectionName, JsonObject filter, int batchSize, AtomicInteger offset) {
        JsonObject accumulated = null;
        while (true) {
            int skip = offset.getAndAdd(batchSize);
            JsonArray documents = fetchBatch(collectionName, filter, skip, batchSize);
            if (documents.isEmpty()) {
                break;
            }
            JsonObject batchSchema = schemaFromDocuments(documents);
            accumulated = accumulated == null ? batchSchema : merge(accumulated, batchSchema);
        }
        return accumulated;
    }

    private JsonArray fetchBatch(String collectionName, JsonObject filter, int skip, int limit) {
        JsonObject findArgs = JsonObject.EMPTY
                .put("collection", collectionName)
                .put("skip", JsonValue.of(skip))
                .put("limit", JsonValue.of(limit));
        if (filter != null) {
            findArgs = findArgs.put("filter", filter.jsonValue());
        }
        JsonObject result = mongoJsonDB.runCommand(JsonObject.EMPTY.put("find", findArgs));
        return result.get(JsonArray.of("cursor", "firstBatch")).getJsonArray();
    }

    private JsonObject schemaFromDocuments(JsonArray documents) {
        JsonObject arraySchema = schemaGenerator.generateSchema(documents);
        JsonValue itemsSchema = arraySchema.get("items");
        return itemsSchema != null ? itemsSchema.getJsonObject() : JsonObject.EMPTY;
    }

    private JsonObject merge(JsonObject schema1, JsonObject schema2) {
        return schemaUnifier.unionOf(schema1.jsonValue(), schema2.jsonValue()).getJsonObject();
    }
}
