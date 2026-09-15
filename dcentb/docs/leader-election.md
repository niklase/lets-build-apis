# Leader election

Implementation: `com.zuunr.dcentb.async.leaderelection` (`LeaderElector`, `LeaderLease`).
Tests: `LeaderElectorIT` (integration test against a real MongoDB replica set — see `README.md`,
"Start MongoDB"; this cannot be tested against a standalone server, both because change streams
need a replica set at all, and because `LeaderElector` itself uses majority write concern).

This is a self-contained primitive: it knows nothing about change streams, task processing, or any
of the rest of the async-processing feature (`docs/async-tasks-processing.md`). It answers exactly
one question — "is this process currently allowed to do the one-at-a-time work for `streamId`?" —
and nothing else. Wire it into anything leader-only by calling `tryAcquireOrRenew()` on a heartbeat
and checking `isStillLeader()` immediately before any side effect that must not happen twice.

## The data model

One document per `streamId`, in a collection you choose (e.g. `async-leader-lease`):

```json
{
  "_id": "default",
  "leaderId": "instance-a1b2c3",
  "generation": 7,
  "leaseExpiresAt": "2026-09-15T14:32:10.123Z",
  "updatedAt": "2026-09-15T14:31:40.123Z"
}
```

- `leaderId` — the instance that currently holds the lease. `null` means unclaimed.
- `generation` — the fencing token. See below.
- `leaseExpiresAt` — an ISO-8601 string (this codebase stores all timestamps as plain sortable
  strings, not BSON dates — see `meta.createdAt` elsewhere; `$lt`/`$gt` on ISO-8601 strings compare
  correctly because the format is fixed-width and zero-padded).

## The protocol: compare-and-swap via upsert, not a transaction

`tryAcquireOrRenew()` issues one `findAndModify` command:

- **query**: `_id == streamId AND (leaderId == null OR leaderId == myInstanceId OR leaseExpiresAt < now)`
- **update**: `$set: {leaderId: me, leaseExpiresAt: now+duration, updatedAt: now}, $inc: {generation: 1}`
- **upsert: true, new: true, writeConcern: {w: "majority"}**

Three outcomes:

1. **No document exists yet.** The query can't match anything, so MongoDB inserts a new one built
   from the update (upsert semantics extrapolate `_id` from the query's equality clause on `_id`).
   `$inc` on a field that doesn't exist starts from 0, so `generation` comes out as `1`. This
   instance is now leader.
2. **A document exists and the query matches** (no leader, expired, or it's already me). MongoDB
   updates it in place. `generation` increments by 1 regardless of whether this is a genuine
   handover or a same-instance renewal — see "why generation always increments" below.
3. **A document exists but the query does *not* match** (someone else holds a valid, unexpired
   lease). With `upsert: true` and a filter that doesn't match, MongoDB attempts to *insert* — and
   fails with a duplicate-key error on `_id`, because a document with that `_id` already exists.
   **This is the whole trick**: a duplicate-key error from an upsert is the CAS-loss signal, not a
   bug to work around. `LeaderElector` catches exactly this (`MongoCommandException` with error
   code `11000`) and returns `LeaderLease.notLeader()`. Any other exception is treated as
   infrastructure trouble, not an election outcome — see "failure philosophy" below.

No multi-document transaction is needed: a single `findAndModify` is already atomic, and that
atomicity is the entire correctness argument. If you ever change this to a multi-statement
operation, you lose that guarantee and need to reach for a transaction instead.

## Why `generation` always increments — the fencing token

`generation` is not "how many times has this document been renewed" — it's a value that is
**unique and strictly increasing across every successful write to this document, by anyone**.
Because only one `findAndModify` can ever win a given write (Mongo serializes writes to a single
document), every successful acquire-or-renew — whether it's a fresh acquire, a takeover, or a
same-instance renewal — gets a `generation` no other write has ever had or will ever have again.

This is what makes `isStillLeader()` correct. It re-reads the document and checks
`leaderId == me AND generation == <the generation I got at my last successful write>`. If another
instance has since taken over, both the `leaderId` and the `generation` in the document have moved
on, so the check fails — even though the original leader's *local* state still says "I'm leader,
because nothing ever told it otherwise." **This is the specific hazard fencing exists for**: a
leader that paused (GC, VM stall, network partition) for longer than its lease TTL, then resumed,
still believes it's leader right up until it tries to do something that touches shared state. Every
leader-only side effect must go through `isStillLeader()` immediately beforehand — not "was I leader
a few heartbeats ago," but "does the database agree, right now, at this exact generation."

`LeaderElectorIT.staleLeaderIsFencedOutAfterFailover` is this scenario end to end: instance A
acquires, its lease expires without renewal, instance B takes over, and A — despite never having
been told it lost leadership — fails `isStillLeader()`.

## Failure philosophy: never assume leadership on uncertainty

`tryAcquireOrRenew()` and `isStillLeader()` never throw for anything that's a legitimate election
outcome. But they also never throw for genuine trouble (timeouts, auth failures, network errors) —
those are caught too, logged at `ERROR` (as opposed to routine lost-contention, which isn't logged
as an error at all), and also resolve to "not leader." The reasoning: an instance that cannot
positively confirm it holds the lease must behave as if it doesn't. It is always safer to have zero
active leaders for a few seconds than to have an instance keep acting as leader because it merely
*failed to find out* that it wasn't. If you need a distinction between "lost the election" and
"couldn't reach MongoDB" for alerting purposes, that's exactly what the log level split is for —
don't try to recover that distinction from the return value.

## Known limitation: lease-expiry comparison uses each instance's local clock

`leaseExpiresAt` and `now` are computed with `Instant.now()` on whichever instance is calling
`tryAcquireOrRenew()`, not MongoDB's server clock. This means the protocol implicitly assumes
reasonably synchronized clocks across instances (NTP or equivalent) — the same assumption nearly
every lease-based leader-election system makes (this is not unique to this implementation). If an
instance's clock is meaningfully ahead of the others', it could renew "early" relative to what a
server-clock-based comparison would allow; if it's meaningfully behind, its own lease could appear
to expire sooner than it locally believes.

This was a deliberate scope trade-off, not an oversight: a server-clock-only design is possible in
MongoDB (`$expr` with the `$$NOW` aggregation variable, and a pipeline-style update), but doing so
would have required extending this codebase's JSON-command query DSL (`Json2BsonQueryTranslator`)
to support `$expr`, which none of its existing consumers need. Given that (a) NTP-synchronized
clocks are the normal operating assumption for this class of system anyway and (b) the blast radius
of a modest clock skew here is "a slightly early or late failover," not silent data loss or a
correctness violation of the fencing guarantee itself (the fencing token is still strictly ordered
by MongoDB regardless of any client's clock), local-clock comparison was the pragmatic choice.
Revisit this — by extending `Json2BsonQueryTranslator` with `$expr` support — if this assumption
ever becomes a real operational problem.

## What this deliberately does not do

- **No scheduling.** `LeaderElector` doesn't run a heartbeat loop itself; callers call
  `tryAcquireOrRenew()` on their own cadence. (The future `ChangeStreamListener` will own this.)
- **No notification on loss of leadership.** There's no callback/listener API — callers must poll
  `isStillLeader()` at the point it matters. This keeps the primitive simple and avoids a whole
  class of "callback fired late/never" bugs; a notification layer can be built on top if needed.
- **No multi-stream coordination.** Each `LeaderElector` instance is independent, scoped to one
  `streamId`. Running several (e.g. one per `streamId` for future horizontal sharding, per
  `docs/async-tasks-processing.md` §9) is just constructing several instances — nothing here assumes
  there's only ever one.
