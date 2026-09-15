package com.zuunr.dcentb.async.changestream;

import org.bson.BsonDocument;
import org.bson.BsonTimestamp;

import java.time.Instant;
import java.util.Objects;

/**
 * A persisted position in a change stream: the opaque resume token (treated as an opaque string per
 * MongoDB's own recommendation — never parsed, only round-tripped) plus the resumed event's
 * clusterTime, stored with full (seconds + increment) precision so it can serve as an exact
 * {@code startAtOperationTime} fallback if the resume token itself is ever rejected as invalid. See
 * docs/change-stream-listener.md.
 */
public final class Checkpoint {

    private final BsonDocument resumeToken;
    private final BsonTimestamp clusterTime;

    public Checkpoint(BsonDocument resumeToken, BsonTimestamp clusterTime) {
        this.resumeToken = Objects.requireNonNull(resumeToken, "resumeToken");
        this.clusterTime = Objects.requireNonNull(clusterTime, "clusterTime");
    }

    public BsonDocument getResumeToken() {
        return resumeToken;
    }

    public BsonTimestamp getClusterTime() {
        return clusterTime;
    }

    /** Human-readable only (dashboards/debugging) — resuming uses the exact {@link #getClusterTime()}. */
    public Instant getClusterTimeAsInstant() {
        return Instant.ofEpochSecond(clusterTime.getTime());
    }

    @Override
    public String toString() {
        return "Checkpoint{clusterTime=" + getClusterTimeAsInstant() + " (t=" + clusterTime.getTime() + ", i=" + clusterTime.getInc() + ")}";
    }
}
