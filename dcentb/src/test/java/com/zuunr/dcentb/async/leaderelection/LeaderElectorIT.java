package com.zuunr.dcentb.async.leaderelection;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.zuunr.mongodb.MongoJsonDB;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link LeaderElector} against a real MongoDB replica set (see public/dcentb/README.md,
 * "Start MongoDB" — a single-node replica set is required; this suite does not run against a
 * standalone server). Every test uses a fresh, random streamId so tests never interfere with each
 * other even when run in parallel, without needing to drop the collection between tests.
 */
class LeaderElectorIT {

    private static final String CONNECTION = "mongodb://admin:adminpassword@localhost:27017/?authSource=admin";
    private static final MongoClient MONGO_CLIENT = MongoClients.create(CONNECTION);
    private static final MongoDatabase DATABASE = MONGO_CLIENT.getDatabase("leaderelectorit");
    private static final MongoJsonDB MONGO_JSON_DB = new MongoJsonDB(DATABASE);
    private static final String COLLECTION = "leader-lease";

    private String streamId;

    @BeforeEach
    void setUp() {
        streamId = "stream-" + UUID.randomUUID();
    }

    @Test
    void firstAcquireSucceedsWithGeneration1() {
        LeaderElector elector = new LeaderElector(MONGO_JSON_DB, COLLECTION, streamId, "instance-A", Duration.ofSeconds(30));

        LeaderLease lease = elector.tryAcquireOrRenew();

        assertTrue(lease.isLeader());
        assertEquals("instance-A", lease.getLeaderId());
        assertEquals(1L, lease.getGeneration());
        assertTrue(elector.isStillLeader());
    }

    @Test
    void renewalBySameInstanceSucceedsAndAdvancesGeneration() {
        LeaderElector elector = new LeaderElector(MONGO_JSON_DB, COLLECTION, streamId, "instance-A", Duration.ofSeconds(30));

        LeaderLease first = elector.tryAcquireOrRenew();
        LeaderLease renewed = elector.tryAcquireOrRenew();

        assertTrue(renewed.isLeader());
        assertEquals(first.getGeneration() + 1, renewed.getGeneration());
        assertTrue(elector.isStillLeader());
    }

    @Test
    void secondInstanceCannotAcquireWhileLeaseValid() {
        LeaderElector electorA = new LeaderElector(MONGO_JSON_DB, COLLECTION, streamId, "instance-A", Duration.ofSeconds(30));
        LeaderElector electorB = new LeaderElector(MONGO_JSON_DB, COLLECTION, streamId, "instance-B", Duration.ofSeconds(30));

        LeaderLease leaseA = electorA.tryAcquireOrRenew();
        assertTrue(leaseA.isLeader());

        LeaderLease leaseB = electorB.tryAcquireOrRenew();
        assertFalse(leaseB.isLeader());

        assertTrue(electorA.isStillLeader(), "the real leader must be unaffected by the other instance's failed attempt");
    }

    @Test
    void failoverHappensOnceLeaseExpires() throws InterruptedException {
        Duration shortLease = Duration.ofMillis(300);
        LeaderElector electorA = new LeaderElector(MONGO_JSON_DB, COLLECTION, streamId, "instance-A", shortLease);
        LeaderElector electorB = new LeaderElector(MONGO_JSON_DB, COLLECTION, streamId, "instance-B", shortLease);

        LeaderLease leaseA = electorA.tryAcquireOrRenew();
        assertTrue(leaseA.isLeader());

        Thread.sleep(400); // simulate A crashing/pausing past its lease TTL without ever renewing

        LeaderLease leaseB = electorB.tryAcquireOrRenew();

        assertTrue(leaseB.isLeader());
        assertEquals(leaseA.getGeneration() + 1, leaseB.getGeneration());
    }

    @Test
    void staleLeaderIsFencedOutAfterFailover() throws InterruptedException {
        Duration shortLease = Duration.ofMillis(300);
        LeaderElector electorA = new LeaderElector(MONGO_JSON_DB, COLLECTION, streamId, "instance-A", shortLease);
        LeaderElector electorB = new LeaderElector(MONGO_JSON_DB, COLLECTION, streamId, "instance-B", shortLease);

        electorA.tryAcquireOrRenew();
        Thread.sleep(400);
        LeaderLease leaseB = electorB.tryAcquireOrRenew();
        assertTrue(leaseB.isLeader());

        // A never learned it lost leadership (nothing pushes that to it) — this is exactly the split-brain
        // hazard the fencing token exists for: A must be rejected the moment it tries to confirm, even
        // though it still locally believes it is leader.
        assertFalse(electorA.isStillLeader(), "a stale ex-leader must be fenced out, not merely 'probably fine'");
        assertTrue(electorB.isStillLeader(), "the genuine new leader must still be confirmed");
    }

    @Test
    void exactlyOneWinnerUnderConcurrentContentionForAFreshLease() throws Exception {
        int contenders = 8;
        ExecutorService pool = Executors.newFixedThreadPool(contenders);
        try {
            CountDownLatch ready = new CountDownLatch(contenders);
            CountDownLatch go = new CountDownLatch(1);
            List<Future<LeaderLease>> futures = new ArrayList<>();

            for (int i = 0; i < contenders; i++) {
                LeaderElector elector = new LeaderElector(MONGO_JSON_DB, COLLECTION, streamId, "instance-" + i, Duration.ofSeconds(30));
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return elector.tryAcquireOrRenew();
                }));
            }

            ready.await();
            go.countDown();

            long winners = 0;
            for (Future<LeaderLease> future : futures) {
                if (future.get(10, TimeUnit.SECONDS).isLeader()) {
                    winners++;
                }
            }
            assertEquals(1, winners, "exactly one contender must win a simultaneous first-time election — this is the split-brain-prevention property");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void releaseAllowsImmediateReacquisitionByAnotherInstance() {
        LeaderElector electorA = new LeaderElector(MONGO_JSON_DB, COLLECTION, streamId, "instance-A", Duration.ofSeconds(30));
        LeaderElector electorB = new LeaderElector(MONGO_JSON_DB, COLLECTION, streamId, "instance-B", Duration.ofSeconds(30));

        electorA.tryAcquireOrRenew();
        electorA.release();

        LeaderLease leaseB = electorB.tryAcquireOrRenew();

        assertTrue(leaseB.isLeader(), "release() should let another instance acquire immediately, without waiting out the lease TTL");
    }

    @Test
    void releaseByNonLeaderDoesNotClobberTheActualLeader() {
        LeaderElector electorA = new LeaderElector(MONGO_JSON_DB, COLLECTION, streamId, "instance-A", Duration.ofSeconds(30));
        LeaderElector electorB = new LeaderElector(MONGO_JSON_DB, COLLECTION, streamId, "instance-B", Duration.ofSeconds(30));

        electorA.tryAcquireOrRenew(); // A leads
        electorB.tryAcquireOrRenew(); // B loses, its local currentLease stays notLeader()
        electorB.release();           // must be a no-op: B was never recorded as leader

        assertTrue(electorA.isStillLeader(), "an unrelated release() call from a losing contender must not affect the real leader's lease");
    }
}
