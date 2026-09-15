package com.zuunr.dcentb.async.leaderelection;

import com.mongodb.MongoCommandException;
import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import com.zuunr.mongodb.MongoJsonDB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;

/**
 * Elastic leader election over a single lease document in MongoDB, implemented as {@code findAndModify}
 * JSON commands through {@link MongoJsonDB} — the same JSON-command abstraction the rest of dcentb uses
 * to talk to Mongo, rather than a parallel raw-driver code path. See docs/leader-election.md for the full
 * protocol writeup (the compare-and-swap-via-upsert trick, the fencing token, and why lease-expiry
 * comparison uses each instance's local clock rather than the server clock) — this class is the
 * implementation of that document, kept deliberately free of any change-stream/task-processing concern
 * so it can be tested and reasoned about on its own.
 *
 * <p>One {@link LeaderElector} instance corresponds to one {@code streamId} (one lease document,
 * {@code _id == streamId}, in the configured collection) and one running application instance
 * ({@code instanceId}, supplied by the caller — must be unique per process). Multiple instances racing
 * for the same {@code streamId} is exactly the scenario this class resolves.
 *
 * <p>This class does not schedule anything itself: callers are responsible for invoking
 * {@link #tryAcquireOrRenew()} on their own heartbeat interval, and for stopping leader-only work the
 * moment {@link #isStillLeader()} returns false.
 */
public final class LeaderElector {

    private static final Logger LOG = LoggerFactory.getLogger(LeaderElector.class);
    private static final int DUPLICATE_KEY_ERROR_CODE = 11000;

    private final MongoJsonDB mongoJsonDB;
    private final String leaseCollection;
    private final String streamId;
    private final String instanceId;
    private final Duration leaseDuration;

    private volatile LeaderLease currentLease = LeaderLease.notLeader();

    public LeaderElector(MongoJsonDB mongoJsonDB, String leaseCollection, String streamId, String instanceId, Duration leaseDuration) {
        if (leaseDuration.isNegative() || leaseDuration.isZero()) {
            throw new IllegalArgumentException("leaseDuration must be positive, was: " + leaseDuration);
        }
        this.mongoJsonDB = mongoJsonDB;
        this.leaseCollection = leaseCollection;
        this.streamId = streamId;
        this.instanceId = instanceId;
        this.leaseDuration = leaseDuration;
    }

    /**
     * Attempts to become (or remain) leader for {@code streamId}. Safe to call repeatedly on a
     * heartbeat — a call by the current leader before its lease expires renews it; a call by anyone
     * else while the lease is still valid fails harmlessly and returns {@link LeaderLease#notLeader()}.
     *
     * <p>Never throws for the expected "someone else already holds a valid lease" outcome — that is
     * reported as a normal {@code notLeader()} result. On genuine infrastructure trouble (timeouts,
     * auth failures, etc.) this also returns {@code notLeader()} rather than propagating, on the
     * principle that an instance which cannot positively confirm it holds the lease must never act as
     * leader; the distinction is only visible in the logs (losing contention is not logged as an error —
     * that is routine — infrastructure trouble is logged at ERROR).
     */
    public LeaderLease tryAcquireOrRenew() {
        Instant now = Instant.now();
        Instant newExpiry = now.plus(leaseDuration);

        JsonObject query = JsonObject.EMPTY.put("$and", JsonArray.of(
                JsonObject.EMPTY.put("_id", JsonArray.of(
                        JsonObject.EMPTY.put("$eq", streamId))),
                JsonObject.EMPTY.put("$or", JsonArray.of(
                        JsonObject.EMPTY.put("leaderId", JsonArray.of(
                                JsonObject.EMPTY.put("$eq", JsonValue.NULL))),
                        JsonObject.EMPTY.put("leaderId", JsonArray.of(
                                JsonObject.EMPTY.put("$eq", instanceId))),
                        JsonObject.EMPTY.put("leaseExpiresAt", JsonArray.of(
                                JsonObject.EMPTY.put("$lt", now.toString())))
                ))
        ));

        JsonObject update = JsonObject.EMPTY
                .put("$set", JsonObject.EMPTY
                        .put("leaderId", instanceId)
                        .put("leaseExpiresAt", newExpiry.toString())
                        .put("updatedAt", now.toString()))
                .put("$inc", JsonObject.EMPTY.put("generation", 1));

        JsonObject command = JsonObject.EMPTY.put("findAndModify", JsonObject.EMPTY
                .put("collection", leaseCollection)
                .put("query", query)
                .put("update", update)
                .put("upsert", true)
                .put("new", true)
                .put("writeConcern", JsonObject.EMPTY.put("w", "majority")));

        try {
            JsonObject result = mongoJsonDB.runCommand(command);
            JsonObject value = result.get("value").getJsonObject();
            long generation = value.get("generation").getLong();
            LeaderLease lease = LeaderLease.leader(instanceId, generation, newExpiry);
            boolean wasLeader = currentLease.isLeader();
            currentLease = lease;
            if (!wasLeader) {
                LOG.info("Acquired leadership of stream '{}' as instance '{}', generation {}", streamId, instanceId, generation);
            }
            return lease;
        } catch (MongoCommandException e) {
            if (e.getErrorCode() == DUPLICATE_KEY_ERROR_CODE) {
                // Expected outcome of losing the race: another instance already holds a valid, unexpired
                // lease, so our upsert's insert path collided on _id. Not an error.
                if (currentLease.isLeader()) {
                    LOG.warn("Lost leadership of stream '{}': another instance now holds the lease", streamId);
                }
                currentLease = LeaderLease.notLeader();
                return currentLease;
            }
            LOG.error("Unexpected MongoCommandException while attempting leader election for stream '{}'", streamId, e);
            currentLease = LeaderLease.notLeader();
            return currentLease;
        } catch (RuntimeException e) {
            LOG.error("Unable to reach MongoDB while attempting leader election for stream '{}' — assuming not leader", streamId, e);
            currentLease = LeaderLease.notLeader();
            return currentLease;
        }
    }

    /**
     * Re-confirms leadership against the database, checking both {@code leaderId} and the fencing token
     * ({@code generation}) recorded at the last successful {@link #tryAcquireOrRenew()}. Callers must
     * call this immediately before any leader-only side effect that must not run twice (e.g. advancing a
     * checkpoint) and abort that side effect if it returns false — this is what rejects a stale ex-leader
     * that paused past its lease TTL and does not yet know it lost leadership.
     */
    public boolean isStillLeader() {
        LeaderLease snapshot = currentLease;
        if (!snapshot.isLeader()) {
            return false;
        }
        JsonObject command = JsonObject.EMPTY.put("find", JsonObject.EMPTY
                .put("collection", leaseCollection)
                .put("filter", JsonObject.EMPTY.put("_id", JsonArray.of(
                        JsonObject.EMPTY.put("$eq", streamId)))));
        try {
            JsonObject result = mongoJsonDB.runCommand(command);
            JsonArray firstBatch = result.get(JsonArray.of("cursor", "firstBatch")).getJsonArray();
            JsonObject doc = firstBatch.size() > 0 ? firstBatch.get(0).getJsonObject() : null;
            JsonValue generation = doc == null ? null : doc.get("generation");
            boolean stillLeader = doc != null
                    && instanceId.equals(doc.get("leaderId", JsonValue.NULL).getString())
                    && generation != null
                    && generation.getLong() == snapshot.getGeneration();
            if (!stillLeader) {
                LOG.warn("Fencing check failed for stream '{}': no longer the recorded leader at generation {}", streamId, snapshot.getGeneration());
                currentLease = LeaderLease.notLeader();
            }
            return stillLeader;
        } catch (RuntimeException e) {
            LOG.error("Unable to reach MongoDB while confirming leadership for stream '{}' — assuming not leader", streamId, e);
            currentLease = LeaderLease.notLeader();
            return false;
        }
    }

    /**
     * Best-effort, immediate release of leadership (e.g. on graceful shutdown) so another instance
     * doesn't have to wait out the full lease TTL. Only clears the lease if this instance is still the
     * recorded leader, so a call racing with someone else already having taken over cannot clobber their
     * lease.
     */
    public void release() {
        LeaderLease snapshot = currentLease;
        if (!snapshot.isLeader()) {
            return;
        }
        JsonObject command = JsonObject.EMPTY.put("update", JsonObject.EMPTY
                .put("collection", leaseCollection)
                .put("updates", JsonArray.of(JsonObject.EMPTY
                        .put("q", JsonObject.EMPTY.put("$and", JsonArray.of(
                                JsonObject.EMPTY.put("_id", JsonArray.of(
                                        JsonObject.EMPTY.put("$eq", streamId))),
                                JsonObject.EMPTY.put("leaderId", JsonArray.of(
                                        JsonObject.EMPTY.put("$eq", instanceId))))))
                        .put("u", JsonObject.EMPTY.put("$set", JsonObject.EMPTY.put("leaderId", JsonValue.NULL))))));
        try {
            mongoJsonDB.runCommand(command);
            LOG.info("Released leadership of stream '{}' (instance '{}')", streamId, instanceId);
        } catch (RuntimeException e) {
            LOG.warn("Failed to release leadership of stream '{}' cleanly — it will expire naturally", streamId, e);
        } finally {
            currentLease = LeaderLease.notLeader();
        }
    }

    /** Last known outcome, without hitting the database. Use {@link #isStillLeader()} to confirm. */
    public LeaderLease currentLease() {
        return currentLease;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public String getStreamId() {
        return streamId;
    }
}
