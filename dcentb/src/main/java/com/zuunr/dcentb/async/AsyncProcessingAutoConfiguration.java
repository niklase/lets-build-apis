package com.zuunr.dcentb.async;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.zuunr.dcentb.async.changestream.ChangeStreamListener;
import com.zuunr.dcentb.async.changestream.CheckpointStore;
import com.zuunr.dcentb.async.config.AsyncProcessingSettings;
import com.zuunr.dcentb.async.leaderelection.LeaderElector;
import com.zuunr.dcentb.async.taskprocessing.ErrorQueueProcessor;
import com.zuunr.dcentb.async.taskprocessing.ErrorQueueStore;
import com.zuunr.dcentb.async.taskprocessing.TopicRouter;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import com.zuunr.json.JsonValueFactory;
import com.zuunr.mongodb.MongoJsonDB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Wires the async-processing pieces built in docs/async-tasks-processing.md (leader election, the
 * change stream listener, topic routing) into a running dcentb application — reusing exactly the same
 * {@code dcentb.openapi.file}/{@code dcentb.mongodb.connection}/{@code dcentb.mongodb.db} properties
 * {@code DcentbAutoConfiguration.requestHandlerProvider} already uses, and the same
 * property-then-OpenAPI-document-fallback resolution for the database name
 * ({@code RequestHandlerProvider.applyMongodbConfig}), so async processing shares the REST side's
 * MongoDB deployment by default rather than needing separate configuration.
 *
 * <p>Entirely opt-in: if the loaded OpenAPI document has no {@code x-dcentb.asyncProcessing} section,
 * {@link AsyncProcessingSettings#parse} returns empty and this configuration constructs no Mongo
 * connection, no listener, nothing — see {@link AsyncProcessingSettings}'s Javadoc for why that
 * matters (most existing deployments don't declare it, and change streams need a replica set a
 * standalone deployment may not have).
 */
@AutoConfiguration
public class AsyncProcessingAutoConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(AsyncProcessingAutoConfiguration.class);
    private static final String DEFAULT_MONGODB_CONNECTION = "mongodb://admin:adminpassword@localhost:27017/?authSource=admin";

    @Bean
    @ConditionalOnMissingBean
    public AsyncProcessingLifecycle asyncProcessingLifecycle(
            @Value("${dcentb.openapi.file:classpath:demo.openapi.json}") Resource openapiResource,
            @Value("${dcentb.mongodb.connection:" + DEFAULT_MONGODB_CONNECTION + "}") String mongodbConnectionString,
            @Value("${dcentb.mongodb.db:}") String configuredDatabaseName
    ) throws IOException {
        JsonObject openApiDocument = JsonValueFactory.create(new String(openapiResource.getInputStream().readAllBytes())).getJsonObject();

        Optional<AsyncProcessingSettings> settingsOptional = AsyncProcessingSettings.parse(openApiDocument);
        if (settingsOptional.isEmpty()) {
            LOG.info("No x-dcentb.asyncProcessing section in the OpenAPI document — async task processing is disabled for this deployment.");
            return new AsyncProcessingLifecycle(null, null);
        }
        AsyncProcessingSettings settings = settingsOptional.get();

        String databaseName = resolveDatabaseName(openApiDocument, configuredDatabaseName);

        MongoClient mongoClient = MongoClients.create(mongodbConnectionString);
        MongoDatabase database = mongoClient.getDatabase(databaseName);
        MongoJsonDB mongoJsonDB = new MongoJsonDB(database);

        String instanceId = UUID.randomUUID().toString();
        LeaderElector leaderElector = new LeaderElector(mongoJsonDB, settings.getLeaseCollection(),
                settings.getStreamId(), instanceId, settings.getLeaseDuration());
        CheckpointStore checkpointStore = new CheckpointStore(mongoJsonDB, settings.getCheckpointCollection(), settings.getStreamId());
        ErrorQueueStore errorQueueStore = new ErrorQueueStore(mongoJsonDB, settings.getErrorQueueCollection());
        TopicRouter topicRouter = new TopicRouter(mongoJsonDB, settings.getMessageLogCollection(), errorQueueStore, settings.getTopics());
        Set<String> watchedCollections = TopicRouter.watchedCollectionsFor(settings.getTopics(), settings.getMessageLogCollection());

        ChangeStreamListener listener = new ChangeStreamListener(database, mongoJsonDB, checkpointStore, leaderElector,
                settings.getHeartbeatInterval(), watchedCollections, topicRouter);

        // Shares leaderElector with the listener above rather than electing independently — see
        // ErrorQueueProcessor's Javadoc on why that's deliberate ("just one listener, one election").
        ErrorQueueProcessor errorQueueProcessor = new ErrorQueueProcessor(errorQueueStore, leaderElector, topicRouter,
                settings.getDeadLetterCollection(), settings.getMaxErrorQueueRequeues(), settings.getErrorQueuePollInterval(),
                settings.getErrorQueueBaseBackoff(), settings.getErrorQueueMaxBackoff());

        LOG.info("Async task processing enabled: instanceId='{}', streamId='{}', db='{}', {} topic(s), watching collections {}",
                instanceId, settings.getStreamId(), databaseName, settings.getTopics().size(), watchedCollections);

        return new AsyncProcessingLifecycle(listener, errorQueueProcessor);
    }

    /** Mirrors RequestHandlerProvider.applyMongodbConfig's db-name resolution: property, else the OpenAPI document's own x-dcentb.mongodb.db. */
    private static String resolveDatabaseName(JsonObject openApiDocument, String configuredDatabaseName) {
        if (configuredDatabaseName != null && !configuredDatabaseName.isBlank()) {
            return configuredDatabaseName;
        }
        String documentDatabaseName = openApiDocument.get("x-dcentb", JsonObject.EMPTY).getJsonObject()
                .get("mongodb", JsonObject.EMPTY).getJsonObject()
                .get("db", JsonValue.NULL).getString();
        if (documentDatabaseName == null || documentDatabaseName.isBlank()) {
            throw new IllegalStateException("x-dcentb.asyncProcessing is configured but no MongoDB database name could be resolved " +
                    "(set dcentb.mongodb.db, or x-dcentb.mongodb.db in the OpenAPI document)");
        }
        return documentDatabaseName;
    }
}
