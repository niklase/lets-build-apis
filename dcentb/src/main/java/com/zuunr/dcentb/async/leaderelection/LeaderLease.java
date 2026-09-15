package com.zuunr.dcentb.async.leaderelection;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable snapshot of a {@link LeaderElector}'s outcome: either "this instance is leader, as of
 * generation N, until leaseExpiresAt" or "not leader". generation is the fencing token — see
 * docs/leader-election.md for why it exists and how callers must use it.
 */
public final class LeaderLease {

    private static final LeaderLease NOT_LEADER = new LeaderLease(false, null, -1L, null);

    private final boolean leader;
    private final String leaderId;
    private final long generation;
    private final Instant leaseExpiresAt;

    private LeaderLease(boolean leader, String leaderId, long generation, Instant leaseExpiresAt) {
        this.leader = leader;
        this.leaderId = leaderId;
        this.generation = generation;
        this.leaseExpiresAt = leaseExpiresAt;
    }

    public static LeaderLease leader(String leaderId, long generation, Instant leaseExpiresAt) {
        Objects.requireNonNull(leaderId, "leaderId");
        Objects.requireNonNull(leaseExpiresAt, "leaseExpiresAt");
        return new LeaderLease(true, leaderId, generation, leaseExpiresAt);
    }

    public static LeaderLease notLeader() {
        return NOT_LEADER;
    }

    public boolean isLeader() {
        return leader;
    }

    /** Only meaningful when {@link #isLeader()} is true. */
    public String getLeaderId() {
        return leaderId;
    }

    /**
     * The fencing token: a value that is unique and strictly increasing across every successful
     * acquire-or-renew of this lease, regardless of which instance performed it. Only meaningful
     * when {@link #isLeader()} is true.
     */
    public long getGeneration() {
        return generation;
    }

    /** Only meaningful when {@link #isLeader()} is true. */
    public Instant getLeaseExpiresAt() {
        return leaseExpiresAt;
    }

    @Override
    public String toString() {
        return leader
                ? "LeaderLease{leader=true, leaderId=" + leaderId + ", generation=" + generation + ", leaseExpiresAt=" + leaseExpiresAt + "}"
                : "LeaderLease{leader=false}";
    }
}
