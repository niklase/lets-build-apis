# The change stream listener

Implementation: `com.zuunr.dcentb.async.changestream` (`ChangeStreamListener`, `CheckpointStore`,
`Checkpoint`). Tests: `ChangeStreamListenerIT` (integration test against a real MongoDB replica set —
see `README.md`, "Start MongoDB").

This is the second building block of async task processing (`docs/async-tasks-processing.md`), built
directly on top of the first (`docs/leader-election.md`). It answers "who consumes the database's
change stream, and how do we not lose or duplicate work while doing it" — and nothing else. It knows
nothing about topics, subscribers, or `TaskProcessor`s; each raw change event is handed to a single
pluggable `eventHandler` callback. Topic routing plugs in there in a later phase without this class
needing to change.

## Why one database-level stream, not one per collection

Per `docs/async-tasks-processing.md` §2.1/§9: a single `db.watch()` means a single leader election,
a single cursor, a single checkpoint — far simpler to operate than N independent elections that could
each fail or flap on their own. The cost is a shared failure domain, which is why every per-event
side effect (dispatch, checkpoint) is individually fenced (see below) rather than trusting a single
"am I still leader" check made once at the top of the loop.

## The collection filter is load-bearing, not an optimization

**This is the one thing most likely to bite you if you extend this class:** `database.watch()` is
database-level, so without a filter it would also observe this listener's own bookkeeping writes —
every leader-election heartbeat, every checkpoint save. Routing those into `eventHandler` is at best
noise and at worst a literal feedback loop (a checkpoint write is itself a change event; handling it
and checkpointing *that* would never settle). This was a real bug during development, not a
hypothetical: `ChangeStreamListenerIT` crashed until a server-side `$match` on `ns.coll` was added to
the watch pipeline.

**Consequence**: `watchedCollections` in the constructor must be the *complete* set of source
collections this listener's caller cares about — never derive it loosely, and never point a
`ChangeStreamListener` at a database that also contains its own lease/checkpoint collections without
this filter in place. Once topic config exists (a later phase), `watchedCollections` is simply the
union of every topic's `sourceCollection`.

## The at-least-once contract, and the two fencing checks

Checkpointing happens *after* an event is handled, matching the same philosophy already established
for leader election (`docs/leader-election.md`, "checkpoint after processing, require idempotency").
A crash between "handler ran" and "checkpoint saved" causes that event to be redelivered on restart —
not lost. `eventHandler` implementations must be idempotent; this is a hard requirement, not a
suggestion, exactly as `docs/async-tasks-processing.md` specifies for `TaskProcessor`s.

Within one iteration of the consume loop, leadership is re-confirmed via
`LeaderElector.isStillLeader()` **twice**, not once:

1. **Immediately before dispatch.** A stale leader (paused past its lease TTL, per the fencing-token
   scenario in `docs/leader-election.md`) must not hand an event to a handler it's no longer entitled
   to be running.
2. **Immediately before the checkpoint write.** Even if leadership was confirmed a moment ago, the
   handler call itself takes real time; leadership could be lost during it. Checkpointing without
   re-confirming would let a fenced-out leader corrupt the position a legitimate new leader is relying
   on.

If either check fails, the in-memory event is simply discarded (not checkpointed) and the whole method
returns to re-run leader election. Nothing is lost: since the checkpoint never advanced past that
event, whichever instance becomes leader next will see it again from the resume token.

**A consequence worth knowing, not fixing**: these fencing checks can spuriously fail if the JVM
thread handling them is interrupted mid-call (e.g. by `stop()` racing an in-flight dispatch) — see
`ChangeStreamListenerIT.waitForCheckpointToSettle` for a concrete example and why it's there. This is
the fail-safe philosophy from `docs/leader-election.md` operating exactly as designed ("never assume
leadership on uncertainty"): an interrupted confirmation is uncertainty, and uncertainty resolves to
"not leader," which means "don't checkpoint" — not a bug, just the at-least-once contract showing up
at a slightly inconvenient moment. Callers (and tests) must expect it.

## Resume policy

On (re)start, `openCursor()`:

1. **No checkpoint exists** → start from now (`database.watch(pipeline).iterator()`, no resume
   options). This is a deliberate scope boundary, not an oversight — see
   `docs/async-tasks-processing.md` §8.1 on backfill: starting from "now" is correct for this phase,
   and a backfill/replay capability can be added later without touching this resume logic, because it
   would just supply a different starting position, the same mechanism as (3) below.
2. **A checkpoint exists** → `resumeAfter(<persisted resume token>)`. The resume token is treated as
   fully opaque (never parsed — see `docs/leader-election.md`'s equivalent principle for the fencing
   token; the same "don't reach into opaque driver values" discipline applies here).
3. **The resume token is rejected** (caught as `MongoCommandException` — e.g. `ChangeStreamHistoryLost`
   because the oplog window was exceeded while no leader was running) → fall back to
   `startAtOperationTime(<persisted checkpoint's clusterTime>)`. `Checkpoint` stores `clusterTime` with
   full precision (seconds *and* increment, not just a human-readable timestamp) specifically so this
   fallback is exact, not approximate. This is the automatic, safe fallback from
   `docs/async-tasks-processing.md` §8.2 — some already-processed events may be redelivered
   (idempotency absorbs this), but nothing between the last checkpoint and now is skipped. The
   *manual* operator-supplied-timestamp break-glass path described in §8.2, for when even the
   persisted `clusterTime` has aged out of the oplog, is not yet wired up here — a real gap, tracked
   for a later phase, not a silent omission.

## What this deliberately does not do yet

- **No topic routing.** `eventHandler` is one `Consumer<JsonObject>` for every watched collection's
  every event. Per-collection dispatch to different `TaskProcessor`s, and the fan-out/subscriber
  machinery for topics with more than one subscriber, is a later phase (`docs/async-tasks-processing.md`
  §9.3 steps 5–6) and plugs in by replacing this one callback with real routing logic — this class does
  not need to change.
- **No retry/error-queue/dead-letter handling.** `eventHandler` throwing is currently just "log it,
  don't checkpoint, re-elect and retry" — there is no `TaskResult` (`SUCCESS`/`RETRY`/`ERROR_QUEUE`/
  `BLOCK_ALL`, per `docs/async-tasks-processing.md` §8.3) yet. That distinction, and the background
  error-queue processor, are later phases.
- **No manual resume-timestamp override**, as noted above.
- **No metrics.** Lag, checkpoint age, and similar observability (§9.3 step 8) are not implemented.
