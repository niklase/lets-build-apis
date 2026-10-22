package com.zuunr.dcentb.async.changestream;

import com.mongodb.client.MongoDatabase;
import com.zuunr.dcentb.async.leaderelection.LeaderElector;
import com.zuunr.dcentb.async.leaderelection.LeaderLease;
import com.zuunr.json.JsonObject;
import com.zuunr.mongodb.MongoJsonDB;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

class ChangeStreamListenerTest {

    @Test
    void isNotRunningBeforeStart() {
        LeaderElector leaderElector = Mockito.mock(LeaderElector.class);
        ChangeStreamListener listener = new ChangeStreamListener(
                Mockito.mock(MongoDatabase.class), Mockito.mock(MongoJsonDB.class), Mockito.mock(CheckpointStore.class),
                leaderElector, Duration.ofSeconds(1), Set.of("widgets"), event -> { });

        assertFalse(listener.isRunning());
    }

    @Test
    void currentLeaseDelegatesToTheLeaderElectorWithoutCallingItTwice() {
        LeaderElector leaderElector = Mockito.mock(LeaderElector.class);
        LeaderLease lease = LeaderLease.leader("instance-1", 7, java.time.Instant.now().plusSeconds(30));
        Mockito.when(leaderElector.currentLease()).thenReturn(lease);

        ChangeStreamListener listener = new ChangeStreamListener(
                Mockito.mock(MongoDatabase.class), Mockito.mock(MongoJsonDB.class), Mockito.mock(CheckpointStore.class),
                leaderElector, Duration.ofSeconds(1), Set.of("widgets"), (JsonObject event) -> { });

        assertSame(lease, listener.currentLease());
        Mockito.verify(leaderElector, Mockito.times(1)).currentLease();
    }
}
