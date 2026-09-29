package com.zuunr.dcentb.async.changestream;

import com.mongodb.MongoCommandException;
import com.mongodb.MongoNamespace;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.changestream.ChangeStreamDocument;
import com.mongodb.client.model.changestream.FullDocument;
import com.zuunr.dcentb.async.leaderelection.LeaderElector;
import com.zuunr.dcentb.async.leaderelection.LeaderLease;
import com.zuunr.json.JsonObject;
import com.zuunr.mongodb.MongoJsonDB;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Single database-level change stream consumer, gated by {@link LeaderElector} so that of however
 * many application instances are running, only the current leader actually consumes and dispatches
 * events. See docs/change-stream-listener.md for the full design writeup (why one DB-level stream,
 * the at-least-once/checkpoint-after-processing contract, and the resume-token-invalid fallback).
 *
 * <p>This class owns exactly: the cursor lifecycle, leader gating (including re-checking the fencing
 * token immediately before dispatch and again immediately before checkpointing — see
 * {@link LeaderElector#isStillLeader()}), and checkpoint persistence. It knows nothing about topics,
 * subscribers, or {@code TaskProcessor}s — each raw event is handed, as a {@link JsonObject}, to a
 * single {@code eventHandler} callback supplied by the caller. Topic routing plugs in via
 * {@code com.zuunr.dcentb.async.taskprocessing.TopicRouter}, which implements {@code Consumer<JsonObject>}
 * and is a valid {@code eventHandler} — this class did not need to change when that was built.
 * {@code eventHandler} may throw {@link HaltListenerException} to permanently halt this listener
 * instance (see its Javadoc) instead of the default log-and-retry-via-re-election behavior for any
 * other exception.
 *
 * <p><b>Important:</b> because this watches the whole database, {@code watchedCollections} must be an
 * exhaustive, exact list of the collections this listener's caller actually cares about — it is not
 * an optimization, it is load-bearing. Without it, the stream would also observe this listener's own
 * bookkeeping writes (the lease collection's heartbeats, the checkpoint collection's own updates),
 * which is at best noise and at worst a feedback loop (a checkpoint write is itself an event; routing
 * it back into {@code eventHandler} and checkpointing *that* would never settle). The watch pipeline
 * enforces this with a server-side {@code $match} on {@code ns.coll} — irrelevant collections are
 * filtered before they ever reach this process, not after.
 *
 * <p>{@code eventHandler} must not throw for expected/recoverable failures — an exception here is
 * treated as "something is badly wrong," logged, and causes this instance to stop consuming and
 * re-run the leader-election loop (the event will be redelivered, to whichever instance becomes
 * leader next, since the checkpoint was not advanced past it).
 */
public final class ChangeStreamListener {

    private static final Logger LOG = LoggerFactory.getLogger(ChangeStreamListener.class);
    private static final Duration POLL_INTERVAL = Duration.ofMillis(200);

    private final MongoDatabase database;
    private final CheckpointStore checkpointStore;
    private final LeaderElector leaderElector;
    private final Duration heartbeatInterval;
    private final Consumer<JsonObject> eventHandler;
    private final MongoJsonDB mongoJsonDB;
    private final List<Bson> watchPipeline;

    private volatile boolean running = false;
    private Thread thread;

    public ChangeStreamListener(MongoDatabase database, MongoJsonDB mongoJsonDB, CheckpointStore checkpointStore,
                                 LeaderElector leaderElector, Duration heartbeatInterval, Set<String> watchedCollections,
                                 Consumer<JsonObject> eventHandler) {
        if (watchedCollections.isEmpty()) {
            throw new IllegalArgumentException("watchedCollections must not be empty — see class Javadoc for why this isn't optional");
        }
        this.database = database;
        this.mongoJsonDB = mongoJsonDB;
        this.checkpointStore = checkpointStore;
        this.leaderElector = leaderElector;
        this.heartbeatInterval = heartbeatInterval;
        this.eventHandler = eventHandler;
        this.watchPipeline = List.of(Aggregates.match(Filters.in("ns.coll", watchedCollections)));
    }

    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        thread = new Thread(this::runLoop, "change-stream-listener-" + leaderElector.getStreamId());
        thread.setDaemon(true);
        thread.start();
    }

    public synchronized void stop() {
        running = false;
        if (thread != null) {
            thread.interrupt();
            try {
                thread.join(Duration.ofSeconds(10).toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            thread = null;
        }
        leaderElector.release();
    }

    private void runLoop() {
        while (running) {
            LeaderLease lease = leaderElector.tryAcquireOrRenew();
            if (!lease.isLeader()) {
                sleepQuietly(heartbeatInterval);
                continue;
            }
            LOG.info("Became leader for stream '{}' — opening change stream cursor", leaderElector.getStreamId());
            consumeUntilLeadershipLostOrStopped();
        }
    }

    private void consumeUntilLeadershipLostOrStopped() {
        try (MongoCursor<ChangeStreamDocument<Document>> cursor = openCursor()) {
            Instant lastRenewal = Instant.now();
            while (running) {
                if (Duration.between(lastRenewal, Instant.now()).compareTo(heartbeatInterval) >= 0) {
                    lastRenewal = Instant.now();
                    if (!leaderElector.tryAcquireOrRenew().isLeader()) {
                        LOG.warn("Lost leadership of stream '{}' mid-consumption — closing cursor", leaderElector.getStreamId());
                        return;
                    }
                }

                ChangeStreamDocument<Document> event = cursor.tryNext();
                if (event == null) {
                    sleepQuietly(POLL_INTERVAL);
                    continue;
                }

                if (!leaderElector.isStillLeader()) {
                    LOG.warn("Fencing check failed before dispatching an event on stream '{}' — discarding in-memory event; " +
                            "it was not checkpointed, so it will be redelivered once leadership is re-established", leaderElector.getStreamId());
                    return;
                }

                dispatch(event);

                if (!leaderElector.isStillLeader()) {
                    LOG.warn("Fencing check failed after dispatching but before checkpointing an event on stream '{}' — " +
                            "not advancing the checkpoint; the event will be redelivered and must be handled idempotently", leaderElector.getStreamId());
                    return;
                }

                checkpointStore.save(event.getResumeToken(), event.getClusterTime());
            }
        } catch (HaltListenerException e) {
            LOG.error("Halting stream '{}' permanently: {} — this requires manual intervention; " +
                    "restarting this process will re-elect, resume from the last checkpoint, and very likely hit the same failure again. See docs/change-stream-listener.md.",
                    leaderElector.getStreamId(), e.getMessage(), e);
            running = false;
            leaderElector.release();
        } catch (RuntimeException e) {
            LOG.error("Error while consuming change stream for '{}' — will re-run leader election and retry", leaderElector.getStreamId(), e);
        }
    }

    private void dispatch(ChangeStreamDocument<Document> event) {
        try {
            eventHandler.accept(toJsonObject(event));
        } catch (HaltListenerException e) {
            throw e; // logged and handled distinctly by the caller — not the generic "will retry" path
        } catch (RuntimeException e) {
            LOG.error("eventHandler threw for an event on stream '{}' — not checkpointing; this event will be redelivered", leaderElector.getStreamId(), e);
            throw e;
        }
    }

    private MongoCursor<ChangeStreamDocument<Document>> openCursor() {
        Optional<Checkpoint> checkpoint = checkpointStore.load();
        if (checkpoint.isEmpty()) {
            LOG.info("No checkpoint found for stream '{}' — starting from now", leaderElector.getStreamId());
            return database.watch(watchPipeline).fullDocument(FullDocument.UPDATE_LOOKUP).iterator();
        }
        try {
            return database.watch(watchPipeline).fullDocument(FullDocument.UPDATE_LOOKUP).resumeAfter(checkpoint.get().getResumeToken()).iterator();
        } catch (MongoCommandException e) {
            LOG.error("Resume token for stream '{}' was rejected ({}) — falling back to the persisted checkpoint's clusterTime {}. " +
                            "Some already-processed events may be redelivered; none should be lost.",
                    leaderElector.getStreamId(), e.getMessage(), checkpoint.get());
            return database.watch(watchPipeline).fullDocument(FullDocument.UPDATE_LOOKUP).startAtOperationTime(checkpoint.get().getClusterTime()).iterator();
        }
    }

    private JsonObject toJsonObject(ChangeStreamDocument<Document> event) {
        JsonObject json = JsonObject.EMPTY
                .put("operationType", event.getOperationTypeString());

        MongoNamespace ns = event.getNamespace();
        if (ns != null) {
            json = json.put("ns", JsonObject.EMPTY
                    .put("db", ns.getDatabaseName())
                    .put("coll", ns.getCollectionName()));
        }

        if (event.getDocumentKey() != null) {
            Document documentKey = Document.parse(event.getDocumentKey().toJson());
            json = json.put("documentKey", mongoJsonDB.toJsonObject(documentKey));
        }

        if (event.getFullDocument() != null) {
            json = json.put("fullDocument", mongoJsonDB.toJsonObject(event.getFullDocument()));
        }

        return json;
    }

    private void sleepQuietly(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
