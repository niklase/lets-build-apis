New feature in dcentb

REST API is not enough to build a backend. Dcentb also needs processing that starts by some client writing to DB. The mechanism I want to use is mongodb changestreams. 

It should be possible to listen to a mongodb collection change stream and have dcentb execute TaskHandler/ChangestreamItemHandler (give me better exxamples of names if that is not a good name) in a similar way as a HTTP request is processed by a RequestHandler. But in the case of a change stream item there is no request and responce. I want to use the vocabolary in the code that is correct for async processing.

The ChangestreamItemHandler needs to know if it is responseible for handling an incoming change stream item or if there is another Changestreamhandler deployed (in some paralell instance of the same Spring boot application) that is alrady handling the change streams items. So there must be some machanism to determine which instance is responsible, this should be done in a document in a mongodb collection. The solution must be elastic so that a leader instance that goes down is recoginsed by some other instance that will become the new leader instance. This mechanism must be well documented and handled as separate concern.  

The ChangestraeamItemHandler should delegate to TaskProcessors. A taskprocessor must always be idempotent and the ChangestreamItemHandler is responsible for storing the the index of the changestreamitems that have been processed so far. 
TaskProcessors that have processed an item {"success": true} (or tell me if there should be other name for this that is common in such scenarios). If the processing fails, the ChangestreamItemHandler should retry (how many times should be configurable) and it should be possible to specify in the TaskProcessor if this means stopping any more tasks to be done or if the non-processed change stream item should be put in a document as a error queue which will eventually be processed again (and it  should be possible to configure how many times a cheange stream item should be put in such error queue before ending up in a dead letter queue (also a mongodb collection).  

How should I separate CHangestreamListeners. Is there any drawbacks if I just have one ChangestreamItemHandler that takes care of all changestreams of the application. Should I have separate collections for failing change streams of different collection (ie collection students should have students-failing-changestreamitemprocessing and scheools shssould have schools-failing-changestreamitemprocessing).  

I do not think this is enough instructions for you and I want you to tell me all things that are unclear and after that create a very well documented plan and decribe how you reason in way that I can read before doing the actual implementation. 

Come up with proposal of how to declare the processing/how to point to which TaskProcessor that should be run. Maybe make it similar to ItemDecorator?)

When it comes to processing a changesteam events it may be a good idea to have processing done in two steps to create a message topic rather than just a queue. First one TaskProcessor (level 1) that is responsible for listening to the original CRUD item write and to put new documents from this write into one document per subscriber (subscribers must be registered in mongodb). A taskProcessor that is s subscriber of such level 2 changestream item will do the actual business logic that should happen while level 1 taskprocessor is more like a base for async message to create a messaging topic style. Does this make sence or should I do it differently?  

I want you to be really critical to my thinking here and ask if i do things that is known as bad practise or if this approach is too naive. I like the idea of just using dcentb (spring boot) and mongodb for both REST API and async API processing. 

---

# Analysis & Plan (response)

Grounded in the existing codebase: the `Processor` chain-of-responsibility (`rest/processor/Processor.java`) that drives every `RequestHandler` over an immutable `com.zuunr.json.JsonObject requestContext`, and the `ItemDecorator` convention (`ItemDecoratorProcessor.resolveDelegate`) that resolves a class by concatenating `x-dcentb.decoratorBasePackage + ".collections." + <name> + ".ItemDecorator"`, with Spring-bean lookup preferred and a reflective `(JsonValue)` constructor as fallback.

Also grounded in a few hard infrastructure facts I checked before writing anything below:

- **MongoDB today is standalone, not a replica set** (all connection strings in the repo, e.g. `mongodb://admin:adminpassword@localhost:27017/?authSource=admin`, have no `replicaSet=` parameter). **Change streams require a replica set** (a single-node RS is enough for dev/test, but it's not the current setup). This is a hard prerequisite, not a detail — see §7.
- The driver stack is `mongodb-driver-sync` 5.8.0 / `spring-data-mongodb` 5.1.0, synchronous, Spring Boot 4.1.0 / Java 17. No reactive stack.
- There is currently **zero** existing scheduling, distributed-locking, leader-election, or change-stream code anywhere in the repo. This is genuinely greenfield — every design decision below is new precedent, which is exactly why it's worth being careful and explicit now.

## 1. Things that are unclear and need your decision before I design further

**Scope & topology**
1. One `ChangeStreamItemHandler` (I'll call it a *listener* below — see §3) for the whole application, watching the whole database with a single change stream and routing by collection, vs. one listener per collection (each with its own cursor and its own leader election)? You asked this explicitly — my recommendation is in §2.1, but I want you to confirm the failure-domain tradeoff you're accepting.

Answer: Go with just one listener

2. Is a single active consumer per stream (i.e., only the leader processes anything, followers are fully idle) an acceptable throughput ceiling, or do you need to scale processing throughput horizontally later (e.g. by sharding topics across multiple independently-elected leaders)? This shapes whether §2.1's "one listener" recommendation is future-proof enough.

Answer: Eventually I will need to scale processing throughput horizontally. So that should be possible but it is important that it is easy to setup the less demanding setup (with just one lister or per collection listener).

6. Do you need an initial backfill (process documents that already existed before the listener was first deployed), or does processing only need to start from "now" going forward? Classic CDC systems (Debezium etc.) treat this as a first-class concern; MongoDB change streams alone only see *changes*, not existing state.

Answer: initial backfill may be needed at some point in time but then i will extend the functionality. Will it hurt me to not decide on this now?



**Ordering, delivery, and idempotency**
4. What ordering guarantee do you actually need? Per-document ordering (all changes to the same `_id` processed in order) is what change streams naturally give you within one collection's stream; cross-collection ordering is not guaranteed and I don't think you should try to build it.

Answer: Per-document should be fine.

5. What's the idempotency key a `TaskProcessor` should dedupe on — the change event's resume token, or `(documentId, operationType, clusterTime)`? Related: should already-processed items be tracked (a processed-set) in addition to the checkpoint, to guard against reprocessing after a crash between "processed" and "checkpoint advanced"? (My recommendation: no separate processed-set — see §2.4, checkpoint *after* processing, and require idempotency to absorb the at-least-once redelivery that causes.)

Answer: Idempotency will be left to the user/developer of dcentb do design. That means making TaskProcessor idempotent and probably thinking about the REST API operations that are used in the TaskProcessor to be idempotent.

6. When retrying inline (before something goes to the error queue), should the change-stream cursor be blocked on that one item (strict per-collection ordering, but a poison item stalls everything behind it), or should the listener checkpoint past it once it's moved to the error queue and let later items proceed (looser ordering, no stall)? You described both retry-then-error-queue and blocking-vs-not without picking one — I need you to pick.

Answer: There may be cases when there should be a total block and other when there should be retry and then error queue. I want the TaskProcessor to have the options of returning error which means retry (should only be possibly with one retry), error queue or block-all-processing. Is this a bad idea? Remember dcentb i multi-purpose backend and these may be a resonable level for users to manage? 

**Fan-out / two-level design**
7. For the level-1/level-2 idea: should level 1 fan out *by writing a full copy of the payload* into one document per subscriber ("fan-out-on-write"), or should it write *one* message/event document plus lightweight per-subscriber checkpoint/offset documents that all reference it ("fan-out-on-read", closer to how Kafka consumer groups work)? These have very different storage/write-amplification and operational profiles — I lay out the tradeoff in §2.3 but you should choose.

Answer: Fanout-on-read is what i want

8. "Subscribers must be registered in mongodb" — is that registration purely a runtime on/off toggle for a `TaskProcessor` that's already deployed in code (safe), or do you want genuinely dynamic behavior (e.g. registering a new subscriber without a deploy, only data)? The latter is a much bigger feature (means the "business logic" itself must be data-driven/interpretable, not just enabled) — I'm assuming you mean the former unless told otherwise.
9. Do you need the two-level design for *every* collection, or is it an opt-in pattern for collections that actually have multiple independent subscribers? I'd default to: single-level (listener → `TaskProcessor` directly) unless a collection declares subscribers, to avoid forcing every collection through unnecessary fan-out machinery.

**Errors, retries, dead-letters**
10. Retry count "should be configurable" — configurable per `TaskProcessor`/topic, or one global default with per-topic override? (I'd assume the latter, matching how `x-dcentb.mongodb.collection` overrides a computed default elsewhere in this codebase.)
11. Backoff strategy between retries: fixed delay, exponential, or immediate (no delay, since a blocked cursor is presumably already costing you)?

Answer: delays should probably be done by putting in error queue / retry queue because TaskProcessors will delay all other processing. Only fully blocking Taskprocessors should have reason to wait and retry directly without beeing put into retry queue

12. When a `TaskProcessor` reports "stop any more tasks" (your phrase) instead of "retry then error-queue" — stop *what*, exactly? Stop only that item's processing chain (skip remaining subscribers/steps for this one event), or halt the entire listener/collection stream until a human intervenes? These are very different blast radii and the doc doesn't say which you meant.

Answer: Should be possible for developer to decide if retry, error or blocking (for everything (make sure good logging shows this!))  error as different responses from TaskProcessor 

**Operational**
13. Do you want lag/health observable from day one (time between an event's `clusterTime` and when it was processed; depth of the error queue and DLQ; whether a leader currently exists), or is that a later phase? I'd push for at least a leader-heartbeat-age metric from day one, since a silently-dead leader with no failover is the single scariest failure mode of this whole design.

Answer: I want the metrics and also good documentation on how to look at it and how it is implemented.

14. Config location: should async-processing config live in the same per-API OpenAPI JSON files (`person.openapi.secret.json` etc., under a new `x-dcentb` key) even though change streams aren't tied to a single REST path/operation the way the rest of that file is, or in a separate config file dedicated to this feature? See §4 for why I lean toward the latter.

Answer: It should be possible to have in the same OpenAPI config file.

## 2. Critical review — is this naive, and where are the known traps?

**Overall: no, this is not a naive idea.** "MongoDB change streams + a hand-rolled lightweight consumer" is a legitimate, well-precedented pattern (it's essentially the "transactional outbox + CDC" pattern minus a broker), and for a team that has already invested in dcentb+MongoDB for the REST side, avoiding the operational cost of standing up Kafka/RabbitMQ for what might be modest async volume is a reasonable call. But by choosing this path you are explicitly taking on responsibilities that a real broker normally gives you for free, and I want to be blunt about exactly which ones, because a few of them are easy to underestimate:

### 2.1 One listener for the whole app vs. one per collection
You asked this directly. A single DB-level change stream (`MongoDatabase.watch()` rather than per-collection `MongoCollection.watch()`) routing events to per-collection `TaskProcessor`s has real advantages: only **one** leader election, one cursor, one checkpoint to reason about operationally — far simpler than N independent leader elections that could each fail/flap independently. The cost is a shared failure domain: a single slow or wedged `TaskProcessor` for one collection can back up processing for every other collection sharing that stream, and the whole app's async processing has a single point of contention (one leader instance doing all the work).

Answer: It should be possible to just have one single DB-level change stream. Evantually there may be need for per collection change streams but that must not be implmented right now

My recommendation: **one DB-level listener, single leader election, routes to per-collection/topic `TaskProcessor`s** — but each `TaskProcessor` invocation must run with a strict timeout and its own error-queue isolation, so one broken processor degrades to "its own events pile up in its own error queue" rather than "nothing in the app processes." This only works if per-item processing stays fast (sub-second); if any topic's processing is inherently slow (e.g. calls a slow downstream system), that's exactly the kind of workload that *should* go through the error/retry path asynchronously rather than block the shared cursor inline — which is answerable once you resolve question 6 above.

Answer: Yes go with these recommendations

### 2.2 Leader election is the correct instinct, but it's easy to get wrong
"One document in a MongoDB collection, elastic failover" is a real, working pattern — it's essentially what tools like ShedLock do, and what Kubernetes does with Lease objects. But the naive version (one instance writes a doc claiming leadership, others poll and back off) has known failure modes you should design around explicitly, not discover in production:

- **Split-brain via clock skew or GC/network pause**: if the leader pauses (GC, VM stall) for longer than its lease TTL, another instance can correctly take over — but the original instance, once it wakes up, doesn't *know* it lost leadership and can keep acting as leader for a moment. You need a **fencing token** (a monotonically increasing leadership generation number, written into the lease document and checked on every checkpoint write) so a stale ex-leader's writes are rejected rather than silently corrupting the checkpoint.
- Use `findOneAndUpdate` with a filter that includes the current lease holder/generation (compare-and-swap semantics), and **majority write concern + majority read concern** on the lease document, so a leader elected during a primary failover can't be "elected" against a stale/rolled-back view.
- Heartbeat interval vs. lease TTL needs a safety margin (e.g. heartbeat every 10s, TTL 30s) so that ordinary GC pauses/network jitter don't trigger spurious failovers, which themselves have a cost (a few seconds of no processing during handoff, and — if you get the fencing token wrong — a real risk of double-processing).

This is exactly the kind of thing you flagged wanting "documented and handled as a separate concern" — I agree, and I'd go further: build and unit-test the leader-election component completely in isolation from change-stream processing, with its own doc describing the fencing-token protocol, before wiring it to anything else.

Answer: Yes, build leader election in isolation. Make sure all edge cases are tested and well documented.

### 2.3 The two-level "topic" design: sound idea, but pick fan-out-on-write vs fan-out-on-read consciously
What you're describing is a real pattern (it's how a lot of hand-rolled pub/sub on top of a document store works), but there are two structurally different ways to build it, and the doc doesn't commit to one:

- **Fan-out-on-write** (what you described): level-1 processor writes one full message document per subscriber. Simple for level-2 processors (each just watches its own document stream, no shared offset bookkeeping), but write-amplified: N subscribers × every event = N writes, and payload is duplicated N times. Fine at low-to-moderate subscriber counts and event rates; gets expensive if either grows a lot.
- **Fan-out-on-read**: level-1 writes *one* message document per event; each subscriber's `TaskProcessor` tracks its own checkpoint (offset) against that single message stream, the way a Kafka consumer group does. Cheaper to write, but now every subscriber needs its own checkpoint document and you've rebuilt (a simplified version of) consumer-group offset tracking — more moving parts, but it's the more standard shape for "topic with multiple subscribers" and scales better.

I don't think one is objectively correct — it depends on your expected subscriber count and event volume (question 7) — but you should pick deliberately rather than defaulting to fan-out-on-write just because it's what came to mind first.

Answer: I want fan-out-on-read as the other alternative will scale poorly


### 2.4 Use MongoDB's native resume tokens, don't invent a custom index
You wrote "storing the index of the changestream items that have been processed" — MongoDB change streams already give you exactly this, as an opaque `resumeToken` on every event, specifically designed to be persisted and handed to `startAfter`/`resumeAfter` on stream restart. Inventing a separate custom index is solving an already-solved problem and risks getting the corner cases (post-invalidation resync, `clusterTime` vs. logical ordering) wrong in a way the driver already handles. Recommendation: persist the resume token (plus, if you want something human-inspectable for debugging/dashboards, the event's `clusterTime` alongside it — but the token is the thing you resume from, not the timestamp).

One real gap to design for: resume tokens can become invalid (e.g. if the oplog window is exceeded while a leader was down, or on some cluster topology changes). You need an explicit, documented policy for "resume token invalid" — at minimum, alert loudly and require a conscious decision (restart from now / trigger a backfill) rather than silently skipping data.

Answer: Maybe it should be possible to set a time-stamp from when it is ok to start. If resume token is invalid and the timestamp is greater than that of the resume token (is there a timestamp in a resume token?), then it is ok to proceed (this would mean we get at least all changes after the timestamp). This way it should be fairly easy to manually start the processing (by changing the timestamp) when resume tokens have been missed. Does this make sence? Any problems with this thinking? 

### 2.5 Checkpoint after processing, and require idempotency — you already got this right
This is the correct default (at-least-once delivery, checkpoint advances only after the `TaskProcessor` reports success, so a crash between "processed" and "checkpoint written" causes reprocessing, not data loss) — and requiring `TaskProcessor`s to be idempotent, as you specified, is the standard way to make that safe. I'd keep this as-is; the alternative (checkpoint-before-processing, "at-most-once") is strictly worse for a task-processing system and I wouldn't recommend it even as an option.

### 2.6 Shared vs. per-collection error/DLQ collections
You asked this directly too. I'd recommend **one shared `async-processing-errors` collection and one shared `async-processing-dead-letters` collection**, each document tagged with `sourceCollection`/`topic`, rather than `students-failing-changestreamitemprocessing` / `schools-failing-changestreamitemprocessing` per collection. Reasons: it's one index/monitoring surface to watch instead of N that grows every time someone adds a new collection to the system, and operational tooling (a dashboard, an alert on "error queue depth > X") doesn't need to know the full list of collections up front. The downside is you lose the ability to set different retention/TTL policies per source collection on the collection itself — if you need that, a `ttlAfter` field per document (with a single TTL index keyed on it) gets you per-item retention without per-collection namespace sprawl.

### 2.7 Where this architecture's ceiling is
Being explicit about the tradeoff you're accepting: with a single elected leader doing all the processing for a stream, you get strong simplicity and correctness guarantees, but processing throughput is capped by one instance's capacity — the "parallel instances" in your design exist for *failover*, not for *scaling out*. If you later need more throughput than one instance can give you, the natural evolution is sharding topics/collections across multiple independently-leader-elected groups (effectively multiple independent streams, each cheap because of §2.1's single-listener design) rather than trying to have multiple followers process concurrently against one stream (that reintroduces exactly the ordering/coordination complexity a real partitioned broker solves for you, and isn't worth hand-rolling).

## 3. Proposed vocabulary

| Your term | Proposed term | Why |
|---|---|---|
| `ChangestreamItemHandler` / `TaskHandler` | **`ChangeStreamListener`** | Matches the actual role (owns the cursor, leader election, checkpointing) and reads naturally next to `RequestHandler` without implying it has "handled" the business logic itself — it delegates that, same as `RequestHandlerBase` delegates to `Processor`s. |
| `TaskProcessor` | **`TaskProcessor`** (kept) | Already good, and deliberately echoes the existing `Processor` base class — same idempotent-unit-of-work shape, just without an HTTP request/response. |
| `{"success": true}` | **`TaskResult`** — a `SUCCESS` / `RETRY` / `FATAL` tri-state, not a boolean | A boolean can't express "fail, but don't retry — dead-letter immediately" vs "fail, retry" vs "fail, and stop processing entirely" in one field, and you described needing exactly those distinct outcomes. This is the same shape as AWS SQS's redrive policy (`maxReceiveCount` before DLQ) and is well-precedented vocabulary — I'd borrow "dead-letter" but keep your `RETRY`/`FATAL` framing rather than inventing new terms. |
| "leader instance" | **leader / follower**, with an explicit **fencing token / leadership generation** | Standard distributed-systems vocabulary; the fencing token is the part that's easy to skip and shouldn't be (§2.2). |
| level-1/level-2 processors | **publisher `TaskProcessor`** (level 1) / **subscriber `TaskProcessor`** (level 2) | Reads clearly as "topic" vocabulary without needing "level" as a magic number. |

Answer: USe the proposed vocabulary

## 4. Proposed declarative configuration — extending the `ItemDecorator` pattern

The `ItemDecorator` convention is single-purpose by design: exactly one class per collection, resolved purely by naming convention (`decoratorBasePackage + ".collections." + name + ".ItemDecorator"`), with no explicit listing anywhere. That works because there's only ever one decorator per collection. Async processing doesn't fit that shape as cleanly — a single collection can have zero, one, or many `TaskProcessor`s (especially once subscribers are involved), and subscriber registration is explicitly meant to be data-driven ("subscribers must be registered in mongodb" — see question 8). So I'd keep the *base-package + reflective-resolution* idea from `ItemDecorator` (consistent developer experience, same Spring-bean-or-reflective-constructor lookup you already have in `ItemDecoratorProcessor.resolveDelegate`), but make the topic/subscriber wiring **explicit and listed**, not purely convention-derived:

```jsonc
// e.g. public/dcentb/src/main/resources/async-processing.secret.json (see §1 q14 — separate from per-API OpenAPI files)
{
  "x-dcentb": {
    "taskProcessorBasePackage": "com.zuunr.dcentb.demo",
    "changeStreamListener": {
      "scope": "database",              // one listener for the whole app — see §2.1
      "mongodb": { "connection": "...", "db": "dcentb-demo" },
      "leaderElection": {
        "collection": "async-leader-lease",
        "leaseDurationSeconds": 30,
        "heartbeatIntervalSeconds": 10
      },
      "checkpoint": { "collection": "async-checkpoints" },
      "errorQueue": {
        "collection": "async-processing-errors",
        "maxRetries": 5,
        "backoff": { "strategy": "exponential", "initialDelaySeconds": 1 }
      },
      "deadLetterQueue": {
        "collection": "async-processing-dead-letters",
        "maxErrorQueueRequeues": 3
      }
    },
    "topics": [
      {
        "name": "students-created",
        "sourceCollection": "students",
        // resolved like ItemDecorator: <basePackage>.collections.students.taskprocessors.StudentsFanoutTaskProcessor
        "taskProcessorClass": "com.zuunr.dcentb.demo.collections.students.taskprocessors.StudentsFanoutTaskProcessor"
      },
      {
        "name": "students-created.emailNotifier",
        "subscribesTo": "students-created",
        "taskProcessorClass": "com.zuunr.dcentb.demo.collections.students.taskprocessors.EmailNotifierTaskProcessor",
        "maxRetries": 2                  // per-topic override of the global default, same pattern as x-dcentb.mongodb.collection overriding a computed default
      }
    ]
  }
}
```

Why a separate config file rather than folding this into each `person.openapi.secret.json`/`demo.openapi.json`: those files are structured entirely around `paths` (REST operations); a change stream on the `students` collection isn't owned by any one REST path/operation, and a `students`-sourced topic might have subscribers that live conceptually under a completely different API/bounded context. A dedicated file (or a small number of them, one per deployable "worker" if you ever split async processing into its own Spring Boot process) keeps that cleanly separate. Happy to fold it into the existing files instead if you'd rather keep one config surface — that's question 14.

`TaskProcessor` resolution would mirror `ItemDecoratorProcessor.resolveDelegate` exactly: try a Spring bean of the named class first (supports `@Component`/`@Autowired`), fall back to a reflective `(JsonValue)` constructor. The interface itself would be a new sibling to `Processor`, not a subclass of it (there's no `requestContext`/HTTP request to carry) — something like:

```java
public interface TaskProcessor {
    TaskResult process(JsonObject changeEvent); // JsonObject, matching the codebase's com.zuunr.json convention (CLAUDE.md)
}
```

## 5. Phased implementation plan

1. **Infra**: switch the local/dev/test MongoDB to a single-node replica set (`rs.initiate()` — this is a drop-in change, existing standalone-mode CRUD code keeps working unmodified) and document it. Nothing else can be tested without this. *(Blocking — see §7.)*
2. **Leader election**, built and tested in complete isolation from change streams: lease document + fencing token + heartbeat, per §2.2. Its own doc, its own tests (including a simulated split-brain/stale-leader scenario).
3. **`ChangeStreamListener`** (single, DB-level, per §2.1/§1 q1): starts only when leader; opens `db.watch()` from the persisted resume token (or `startAtOperationTime`/now on first run); routes each event by `ns.coll` to registered topics; checkpoints the resume token after successful routing.
4. **`TaskProcessor` SPI + resolution**, mirroring `ItemDecoratorProcessor` (§4), plus the retry/backoff/error-queue/DLQ machinery (`TaskResult` handling per §3, retry counts/backoff per topic per §4).
5. **Publisher/subscriber (topic) support** — only after you've picked fan-out-on-write vs. fan-out-on-read (§2.3/§1 q7).
6. **Observability**: leader-heartbeat-age, per-topic checkpoint lag, error-queue and DLQ depth — at minimum as logged/queryable state even if a full metrics pipeline is a later phase (§1 q13).
7. **Tests**: an integration harness analogous to `ControllerIT`/`GivenWhenThenTesterBase`, but driving Mongo writes as "given" and asserting on `TaskProcessor` side effects as "then"; plus a chaos test that kills the leader mid-stream and asserts a follower takes over and no event is skipped or double-counted beyond what idempotency already tolerates.

## 6. What I need from you before writing any code

Please go through §1 (14 questions) — they're grouped so you don't have to answer all of them with equal care; the ones I'd most want a firm answer on before starting are **1, 6, 7, and 12**, since they change the shape of the core listener/processor contract rather than just a config default.

## 7. Blocking infra note

To repeat, because it's easy to lose in a long document: **change streams do not work against the standalone MongoDB this repo currently uses everywhere** (no `replicaSet=` in any connection string, no docker-compose/testcontainers replica-set setup found anywhere in the repo). Step 1 of any implementation has to be converting local/dev/test Mongo to at least a single-node replica set. This doesn't affect existing REST/CRUD functionality (standalone-mode code keeps working identically against a single-node RS), but it is a real environment change worth calling out and doing deliberately rather than discovering as a blocker mid-implementation.

---

# Follow-up (response to your answers)

I checked one more fact before relying on it: whether a running dcentb instance loads a single OpenAPI config file or several at once. Confirmed **strictly one file per instance** — `DcentbAutoConfiguration` wires exactly one `Resource` (property `dcentb.openapi.file`, default `classpath:demo.openapi.json`) into a single `RequestHandlerProvider`; there's no array/list/directory-scan variant anywhere, in production wiring or the test harness. This matters because it removes a problem I was worried about: with only ever one OpenAPI document loaded, putting async config inside that same document (your answer to Q14) has no cross-file-merging question to solve — it's a section of "the" config, exactly like `x-dcentb.collections` already is.

## 8. Answers to your direct questions, and what's still open

### 8.1 Backfill — "will it hurt me to not decide now?"
No. The listener will default to "start watching from now" on first run. Backfill later is additive (a one-off script/replay over existing documents) and doesn't touch the core checkpoint model, because that model already needs to support starting from an arbitrary past timestamp for a different reason (§8.2 below) — so backfill later is "supply a timestamp once," not a redesign. Nothing for you to decide now.

### 8.2 Resume-token-invalid recovery via timestamp — "does this make sense?"
Yes, with one correction on mechanism. Don't parse the resume token to get a timestamp out of it — it's opaque and its binary layout isn't a stable public API across driver versions. You don't need to: change streams have a *separate* resume mechanism built for exactly this, `startAtOperationTime(BsonTimestamp)`, which starts fresh from a plain clock timestamp instead of a token. Design:

- Persist `clusterTime` alongside the resume token on every checkpoint (you'd already agreed to store this for human-inspection — it now has a functional job too).
- On startup: try `resumeAfter(<token>)`. If the server rejects it (`ChangeStreamHistoryLost`), **automatically** fall back to `startAtOperationTime(<persisted clusterTime>)` — safe by construction, since it's the same point already checkpointed; the only cost is reprocessing a few events, which idempotency already has to absorb.
- Reserve a **manual** operator-supplied timestamp override as the break-glass path for when even the persisted `clusterTime` has aged out of the oplog (leader down for days). That's a real, permanent gap — log it loudly and write an audit-trail document recording exactly what range was skipped.

So: automatic fallback = no data loss, just reprocessing. Manual override = explicit, logged, accepted loss of a specific range. No need to ever read inside the token itself.

### 8.3 Your refined TaskProcessor outcomes (RETRY / error queue / block-all) — is this a bad idea?
No — it's a reasonable level of control for a multi-purpose backend, and it has real precedent: it's structurally the same as SQS/Azure Service Bus exposing explicit "complete / retry / dead-letter" decisions to the consumer rather than a boolean. I'm replacing my earlier `SUCCESS/RETRY/FATAL` three-state with your shape: **`TaskResult` = `SUCCESS` / `RETRY` / `ERROR_QUEUE` / `BLOCK_ALL`**, and per your Q11 answer, only `RETRY` and `BLOCK_ALL` are allowed to delay the cursor inline — `ERROR_QUEUE` always checkpoints past the item and lets a separate background poller handle backoff, so a struggling topic never blocks unrelated processing.

One gap your description left open that I need before implementing: **`RETRY` is capped at one immediate inline attempt — what happens if that retry also fails?**

Answer: Yes retry that fails once will go to error queue

Confirmed — `onRetryFailure: "errorQueue"` in §9.2 is final, not just a default.

One thing I'd like your opinion on, not just adding unasked: a **circuit breaker per topic** — if one `TaskProcessor` produces N consecutive `RETRY`/`ERROR_QUEUE` outcomes in a row, auto-pause *that topic only* (not everything) until an operator clears it, instead of quietly filling the error queue for hours during a downstream outage. I'd treat this as later-phase scope either way — flagging it now so skipping it is a conscious choice, not an oversight.

### 8.4 Still open — need your call before I start building
- **Q8 (dynamic subscriber registration)** — you didn't answer this one. Concretely: when you wrote "subscribers must be registered in mongodb," did you mean (a) subscriber *code* is always declared via `x-dcentb.asyncProcessing.topics` in the OpenAPI config (my assumption below), and "registered in mongodb" really just meant the per-subscriber *offset* bookkeeping lives in Mongo — or (b) you want subscribers addable as pure data (a Mongo collection of `{topic, subscriberName, taskProcessorClass}` documents), with no OpenAPI change or redeploy needed? (b) is materially bigger — it turns "which processor class runs" into runtime-mutable data, which needs its own trust/validation story (arbitrary class names from a database is a code-execution surface, not just config). I've assumed (a) below.

Answer: Yes - in OpenAPI doc

- **Q9 (opt-in fan-out per collection)** — I think this is now answered by the config shape itself (§9.2): a topic only gets a message-log + subscriber offsets when it actually declares subscribers; a collection with exactly one processor can attach that `TaskProcessor` directly to the raw source-collection stream, no fan-out machinery at all. Confirm that's what you want, or tell me you'd rather every collection always go through the same fan-out path for a simpler mental model (at the cost of one extra write per event even for single-subscriber topics).

Answer: Go with opt-in (only apply two levels when there is more than one subscriber)

Everything else from the original 14 I've made a call on rather than re-asking (global-default-with-per-topic-override for retry counts; one shared message-log collection and one shared subscriber-offsets collection rather than per-topic ones — same reasoning you already agreed to in §2.6 for error/DLQ collections). Flagging these as decisions, not silent assumptions — say so if any should change.

## 9. Revised plan — what I will build, in order

This supersedes §4/§5, folding in: single global listener with a forward-compatible `streamId` (so a future horizontal split per your Q2 answer is a config change, not a rewrite), fan-out-on-read, config living inside the one loaded OpenAPI document, the four-outcome `TaskResult`, and the resume-token/timestamp fallback.

### 9.1 How the pieces fit together
One `ChangeStreamListener`, started only on the leader, opens a single `db.watch()`. It routes every event by source collection against `x-dcentb.asyncProcessing.topics`. Each topic declares one or more `subscribers`, and the fan-out mechanism is **derived from the count, not a separate flag** (per your Q9 answer):
- **Exactly one subscriber**: that `TaskProcessor` is invoked directly on the raw event. No message log, no offsets collection, no extra write — the simple case stays simple.
- **More than one subscriber**: a **publisher** step writes one document into a shared `async-topic-messages` collection (tagged with `topic`). Because that collection lives in the same watched database, the *same* listener/cursor picks up its inserts too and routes them to each **subscriber** `TaskProcessor`, tracked via a shared `async-subscriber-offsets` collection keyed by `(topic, subscriberName)`. No second listener, no second leader election — consistent with your "just one listener" answer, and this is exactly what makes fan-out-on-read cheap here rather than needing its own polling loop.

### 9.2 Config shape (revised — lives inside the one loaded OpenAPI document, e.g. `person.openapi.secret.json`)

```jsonc
{
  "x-dcentb": {
    "taskProcessorBasePackage": "com.zuunr.dcentb.demo",
    "asyncProcessing": {
      "streamId": "default",
      "leaderElection": {
        "collection": "async-leader-lease",
        "leaseDurationSeconds": 30,
        "heartbeatIntervalSeconds": 10
      },
      "checkpoint": { "collection": "async-checkpoints" },
      "resume": {
        "onHistoryLost": "fallbackToLastCheckpointTime",
        "manualResumeAtTimestamp": null
      },
      "onRetryFailure": "errorQueue",
      "errorQueue": {
        "collection": "async-processing-errors",
        "maxRetries": 5,
        "pollIntervalSeconds": 30
      },
      "deadLetterQueue": {
        "collection": "async-processing-dead-letters",
        "maxErrorQueueRequeues": 3
      }
    },
    "topics": [
      {
        "name": "students",
        "sourceCollection": "students",
        "subscribers": [
          { "name": "audit", "taskProcessorClass": "com.zuunr.dcentb.demo.collections.students.taskprocessors.StudentsAuditTaskProcessor" }
        ]
      },
      {
        "name": "students-created",
        "sourceCollection": "students",
        "subscribers": [
          { "name": "emailNotifier", "taskProcessorClass": "com.zuunr.dcentb.demo.collections.students.taskprocessors.EmailNotifierTaskProcessor", "maxRetries": 2 },
          { "name": "billingSync", "taskProcessorClass": "com.zuunr.dcentb.demo.collections.students.taskprocessors.BillingSyncTaskProcessor" }
        ]
      }
    ]
  }
}
```

The first topic (`students`, one subscriber) attaches `StudentsAuditTaskProcessor` directly to the raw change stream — no fan-out. The second (`students-created`, two subscribers) automatically gets the message-log + per-subscriber-offset treatment. Same config shape either way; the runtime decides based on `subscribers.length`.

### 9.3 Build order
1. **Infra**: single-node replica set for local/dev/test Mongo. Blocking, unchanged from §5.
2. **Leader election**, in isolation: lease document keyed by `streamId`, fencing token, heartbeat/TTL safety margin, split-brain tests. Own doc.
3. **Checkpoint persistence**: resume token + `clusterTime`, `onHistoryLost` automatic fallback, manual override. Tested independently of the listener.
4. **`ChangeStreamListener`**: single DB-level `db.watch()`, leader-only, reads `x-dcentb.asyncProcessing`/`topics` from the one loaded OpenAPI document, routes direct-attach topics and publisher writes.
5. **Fan-out routing**: message-log inserts routed to subscribers via `async-subscriber-offsets`, as described in §9.1 — no separate listener.
6. **`TaskProcessor` SPI + resolution** (Spring-bean-first, reflective-constructor-fallback, mirroring `ItemDecoratorProcessor.resolveDelegate`) and `TaskResult` handling (`SUCCESS`/`RETRY`/`ERROR_QUEUE`/`BLOCK_ALL`, with `onRetryFailure` escalation per §8.3).
7. **Background error-queue processor**: leader-only, polls `async-processing-errors` on `pollIntervalSeconds`, retries, moves to `async-processing-dead-letters` after `maxErrorQueueRequeues`.
8. **Observability + docs**: leader/heartbeat status, per-topic and per-subscriber lag, error/DLQ depth — I'll check whether Micrometer/Actuator is already a dependency before committing to a concrete metrics mechanism, plus a doc explaining what each number means and where to look, per your Q13 answer.
9. **Tests**: leader-election isolation tests (incl. simulated split-brain/stale-leader), a `ChangeStreamIT`-style harness in the same given/when/then spirit as `ControllerIT` (given: Mongo writes; then: `TaskProcessor` side effects + offset state), and a chaos test that kills the leader mid-stream and asserts a follower takes over with no event skipped or lost.

## 10. Before I start

Resolved: Q8 (subscribers declared in the OpenAPI doc, not dynamic Mongo-registered data), Q9 (fan-out is opt-in, derived from subscriber count — one subscriber attaches directly, more than one gets the message-log/offsets treatment), and `RETRY`-escalation (a failed retry goes to the error queue, final, not just a default).

The **per-topic circuit breaker** (§8.3) is confirmed deferred — added later, once the error queue exists and real failure patterns are visible. Not in §9.3's initial build.

Answer: circuit breaker for repeated errors can be added later

With that, I'm ready to start on **§9.3 phase 1: converting local/dev/test MongoDB to a single-node replica set**, then phase 2 (leader election, built and tested in isolation). I'll check in with you again before moving past phase 2 into the listener itself, since that's where the design becomes harder to cheaply change.

## 11. Phase 1 status: done

Converted the running local `mongodb` Docker container (image `mongodb/mongodb-community-server:latest`) to a single-node replica set (`rs0`) in place, reusing its existing data volumes — no data loss (`dcentb-demo` database confirmed intact afterward). One real gotcha discovered along the way and now documented in the README: with authorization enabled (the `MONGO_INITDB_ROOT_USERNAME`/`PASSWORD` setup this project already uses), `mongod` additionally requires an internal cluster-authentication **keyFile** as soon as `--replSet` is added — even for a single node — or it exits immediately on startup. Generated one into a new named `mongodb_keyfile` Docker volume, owned by the image's internal `mongodb` user (uid `1000`), and wired `--keyFile` alongside `--replSet` into the container's startup command.

Verified:
- `rs.status()` → `PRIMARY`, single healthy member.
- Existing data survived (`dcentb-demo` database present with prior collections).
- `mvn -pl public/dcentb test -Dtest=ControllerIT` → all 3 existing test cases still pass unmodified against the replica-set server — confirms this change is transparent to existing CRUD/REST functionality, as expected.
- Smoke test: `db.students.watch()` now opens successfully (this would fail outright against the old standalone server) — change streams are usable.

`public/dcentb/README.md` (`#### 2. Start MongoDB`) updated with the full procedure: fresh setup (replSet + keyFile from the start) and converting an already-running standalone container in place, both using the exact commands actually run here.

Next: **phase 2, leader election**, built and tested in isolation per §9.3.

## 12. Phase 2 status: done

`com.zuunr.dcentb.async.leaderelection` — `LeaderLease` (immutable outcome value) and `LeaderElector`
(the CAS-based election primitive). Full protocol writeup: `docs/leader-election.md`.

**Architecture correction mid-build**: my original draft used the raw MongoDB driver
(`com.mongodb.client.MongoCollection`) directly for the CAS `findAndModify`, on the grounds that the
existing `com.zuunr:mongodb` JSON-command abstraction (`MongoJsonDB`) didn't support `findAndModify`
at all — its schema blocked the command entirely and its translator had a real bug (the `update`
field was translated as a `List<Document>`, which is wrong for a plain update; only valid for
pipeline-style updates). You asked me to extend `MongoJsonDB` instead of going around it, which was
the right call — going raw would have started a second, inconsistent way of talking to Mongo
alongside the one the whole rest of dcentb uses. Concretely, in `public/mongodb`:

- `mongodb.schema.json`: added `findAndModify` as a valid top-level command, plus `WriteConcern`/
  `ReadConcern` defs (`{w: ...}` / `{level: ...}`) so majority concern can be requested per-command,
  the same way any other Mongo command option is expressed in this DSL.
- `FindAndModifyCommandTranslator.java`: fixed the `update`-as-list bug, added `upsert`,
  `writeConcern`, `readConcern` support.
- Found and fixed one unrelated pre-existing bug this surfaced: `MongoJsonDBIT`'s `delete.json` test
  fixture used a dead `"exactMatch": false` key that the test framework never actually reads (the
  real key is `meta.validationStrategy`) — it was silently doing exact-matching this whole time, and
  only broke once phase 1's replica-set conversion caused delete-command responses to include
  extra RS-only metadata fields (`electionId`, `opTime`, etc.) that a standalone server doesn't
  return. Fixed by using the actual supported key.

One consequence of going through `MongoJsonDB`'s existing query DSL rather than raw driver calls:
lease-expiry comparison (`leaseExpiresAt < now`) uses each instance's **local clock**, not MongoDB's
server clock via `$expr`/`$$NOW` (which would have required extending the query DSL itself to
support `$expr`, unlike the other extensions above which stayed within its existing shape). This is
a real, documented trade-off — see "Known limitation" in `docs/leader-election.md` — not silently
dropped from the earlier `$$NOW`-based design in §2.4. Standard lease-based election assumption
(reasonably synchronized clocks); revisit only if it becomes an actual operational problem.

Verified:
- `LeaderElectorIT` (8 tests, real replica set): first acquire → `generation=1`; same-instance
  renewal advances `generation`; a second instance cannot acquire while the lease is valid; failover
  once the lease expires; **the stale-ex-leader-is-fenced-out scenario** (A acquires, its lease
  expires without renewal, B takes over, A still locally believes it's leader but fails
  `isStillLeader()`); **8 threads racing a fresh election simultaneously → exactly one winner**
  (the split-brain-prevention property); `release()` lets another instance acquire immediately;
  `release()` from a losing contender is a safe no-op that doesn't clobber the real leader.
- No regressions: `MongoJsonDBIT` (10/10, after the `delete.json` fix), `ControllerIT` (3/3).
- Noted, not fixed (pre-existing, unrelated, confirmed via `git log` to predate this session):
  `PersonQueryExampleIT`'s `bson-types-mapping-examples.json` fixture has a plain JSON syntax error
  and fails to parse. Flagging for you to triage separately — out of scope here.

Next: **phase 3, the `ChangeStreamListener`** (single, DB-level, leader-gated) per §9.3.

## 13. Phase 3 status: done

`com.zuunr.dcentb.async.changestream` — `Checkpoint`, `CheckpointStore`, `ChangeStreamListener`. Full
design writeup: `docs/change-stream-listener.md`.

**API-shape check before starting**: unlike `findAndModify`, a change stream is a long-lived tailable
cursor (`db.watch()` → repeated `getMore` calls), not a request/response command — it has no shape
`MongoJsonDB.runCommand(JsonObject) → JsonObject` could represent. Checked with you first rather than
repeating the raw-driver assumption from phase 2: confirmed native driver for the cursor itself only,
`MongoJsonDB` for everything else (checkpoint persistence, event-to-JSON conversion). Added one small,
generically useful method to `MongoJsonDB` — `toJsonObject(Document)` — exposing its existing
`Document → JsonObject` conversion (already used internally for every `runCommand` result) so
`ChangeStreamListener` doesn't need a second, parallel conversion path for events it gets from the
native cursor instead of from `runCommand`.

**A real bug caught before it shipped, not just in review**: the first version of
`ChangeStreamListenerIT` crashed with NullPointerExceptions. Root cause: `database.watch()` is
database-level, so without filtering it also observes the listener's *own* bookkeeping writes (lease
heartbeats, checkpoint saves) — a feedback loop, not just noise. Fixed by adding a server-side
`$match` on `ns.coll` to the watch pipeline, requiring callers to pass an explicit, exhaustive
`watchedCollections` set. Documented prominently in `docs/change-stream-listener.md` as load-bearing,
since it's exactly the kind of thing a future edit could silently break.

Design decisions:
- **Checkpoint precision**: `Checkpoint` stores the resume token as an opaque canonical-JSON string
  (never parsed — same "don't reach into opaque values" discipline as the leader-election fencing
  token) plus `clusterTime` with full seconds+increment precision (not just a human-readable
  timestamp), so the `startAtOperationTime` fallback from §8.2 is exact when a resume token is
  rejected, not approximate.
- **Checkpoint after processing, fenced twice**: leadership is re-confirmed immediately before
  dispatch *and* immediately before the checkpoint write — not just once at the top of the loop — per
  the same fencing-token discipline as `docs/leader-election.md`. A spurious failure of either check
  (e.g. from a shutdown interrupt racing in-flight work) is treated as the at-least-once contract
  working as designed, not a bug — see `docs/change-stream-listener.md` for why, and
  `ChangeStreamListenerIT.waitForCheckpointToSettle` for how the tests account for it honestly rather
  than papering over it.
- **`eventHandler` is a single pluggable callback** for now — no topic routing, no `TaskResult`,
  no error queue. Those are phases 5–6 and plug in without this class changing.

Verified, `ChangeStreamListenerIT` (3 tests, real replica set): an insert on the watched collection is
observed and delivered as a `JsonObject` with `operationType`/`ns`/`documentKey`/`fullDocument`; a
restarted listener resumes from the checkpoint and does not replay an already-processed event; a
second instance takes over and continues consuming after the first stops. No regressions:
`MongoJsonDBIT` (10/10), `LeaderElectorIT` (8/8), `ControllerIT` (3/3).

Next: **phase 4 onward — topic routing, the `TaskProcessor` SPI, and `TaskResult` handling**
(`SUCCESS`/`RETRY`/`ERROR_QUEUE`/`BLOCK_ALL`, per §8.3) per §9.3 steps 5–6.

## 14. Phases 4–6 status: done

`com.zuunr.dcentb.async.taskprocessing` — `TaskResult`, `TaskProcessor`, `TaskProcessorResolver`,
`Topic`, `Subscriber`, `TopicRouter`, `ErrorQueueStore`. Plus one addition to the `changestream`
package: `HaltListenerException`, the contract `ChangeStreamListener` now exposes for "halt this
listener permanently" rather than the default "log it and retry via re-election."

**A real design simplification found during implementation, not just an optimization**: §9.1/§9.2
sketched a per-subscriber `async-subscriber-offsets` collection, modeled on Kafka consumer-group
offsets, to let each fan-out subscriber track its own independent position. Building `TopicRouter`
surfaced that this solves a problem that doesn't exist here: because one `ChangeStreamListener`
drives fan-out synchronously (a message-log insert is observed once, and every subscriber for that
topic is dispatched to in the same call, before the single shared checkpoint advances), there is no
independent per-subscriber pace to track — all subscribers advance in lockstep with the one cursor.
Kafka's consumer groups need offsets because independent consumers pull independently; nothing here
does. Dropped the offsets collection entirely rather than build bookkeeping with no correctness
purpose. Documented prominently in `TopicRouter`'s Javadoc so this isn't quietly rediscovered later.

**`TaskProcessorResolver`** mirrors `ItemDecoratorProcessor.resolveDelegate` (Spring-bean-first,
reflective-`(JsonValue)`-constructor fallback) exactly, minus the base-package-plus-naming-convention
part — each subscriber names its `taskProcessorClass` explicitly in config (§9.2), so there's no
convention to derive, and a missing/wrong class is treated as a real config error (throws) rather
than ItemDecorator's "optional, silently no-op" behavior, since here it was never optional.

**`TaskResult` handling, exactly as confirmed in §8.3/§8.4**: `SUCCESS` → done. `RETRY` (or a thrown
exception, or a null return — both treated the same as `RETRY`, not specially) → exactly one inline
retry; a second failure escalates to `ERROR_QUEUE`, unconditionally, per your confirmed answer.
`ERROR_QUEUE` → written to the shared `ErrorQueueStore`, checkpoint still advances (one struggling
subscriber never blocks anything else). `BLOCK_ALL` → thrown onward as `HaltListenerException`,
which halts the *entire* listener ("for everything," per your Q12 answer) — not just that topic.

**`BLOCK_ALL`'s honest limitation**: the halt does not survive a process restart — a new/restarted
instance will re-elect, resume from the last checkpoint, and very likely hit the same failure and
halt again. Safe (never silently resumes normal processing), but not silent, and not yet a persisted
"stay blocked across restarts" mechanism. Flagged in `HaltListenerException`'s Javadoc as a known,
deliberate gap, not discovered later as a surprise.

**Scope boundary, explicit**: this phase is the routing/dispatch engine, fully tested against real
`Topic`/`Subscriber` Java objects constructed directly. Two things remain, deliberately not bundled
in: (1) parsing `x-dcentb.asyncProcessing`/`topics` out of the OpenAPI document into `Topic`/
`Subscriber` objects, and (2) Spring Boot auto-configuration to actually construct and `start()` a
`ChangeStreamListener` + `TopicRouter` at application startup. Both are "wire the already-built
pieces into the app" work, not new design — kept separate so this phase's checkpoint is about the
routing logic being correct, not about config-parsing plumbing.

Verified, `TopicRouterIT` (9 tests) + `TaskProcessorResolverTest` (3 tests), real MongoDB for the
message-log/error-queue writes: direct-attach dispatch; retry-then-success never reaches the error
queue; retry-then-failure reaches it exactly once; an uncaught exception is treated identically to an
explicit retry; explicit `ERROR_QUEUE` skips the inline retry; `BLOCK_ALL` throws
`HaltListenerException`; an event for an unrecognized collection is ignored, not an error; a fan-out
topic publishes exactly *one* message-log document regardless of subscriber count, and dispatching
that one insert reaches every subscriber with the original payload intact; `watchedCollectionsFor`
includes the message-log collection only when some topic actually fans out. No regressions:
`MongoJsonDBIT` (10/10), `LeaderElectorIT` (8/8), `ChangeStreamListenerIT` (3/3), `ControllerIT`
(3/3) — 26 tests total across the `dcentb` module.

Next: **phase 7, the background error-queue processor** (polls `async-processing-errors`, retries
with backoff, moves to a dead-letter collection after `maxErrorQueueRequeues`) — or, if you'd rather
see this wired into a running app first, the config-parsing + Spring auto-configuration boundary
noted above.

## 15. Wiring into a runnable app: done

`com.zuunr.dcentb.async.config.AsyncProcessingSettings` (parses `x-dcentb.asyncProcessing`/`topics`
out of the one loaded OpenAPI document) and `com.zuunr.dcentb.async.AsyncProcessingAutoConfiguration`
+ `AsyncProcessingLifecycle` (constructs and starts/stops everything as Spring beans), registered
alongside `DcentbAutoConfiguration` in `META-INF/spring/....AutoConfiguration.imports`.

**Deliberately reuses, not reinvents, the REST side's setup**: the same `dcentb.openapi.file`/
`dcentb.mongodb.connection`/`dcentb.mongodb.db` properties `DcentbAutoConfiguration.requestHandlerProvider`
already uses, and the same property-then-document-fallback resolution for the database name that
`RequestHandlerProvider.applyMongodbConfig` uses — so async processing shares the REST side's MongoDB
deployment by default, no separate configuration needed. Only settings the code actually reads are
parsed (see `AsyncProcessingSettings`'s Javadoc for the list of earlier-sketched-but-not-yet-consumed
fields deliberately left unparsed — `resume` policy knobs, per-subscriber `maxRetries`,
`deadLetterQueue`, etc. — so config never silently claims to do something the code doesn't do yet).

**Opt-in, verified, not just asserted**: `x-dcentb.asyncProcessing` absent → `AsyncProcessingSettings.parse`
returns empty → no MongoDB connection is even opened for async processing, nothing starts. Ran the
packaged app against `person.openapi.secret.json` (which has no `asyncProcessing` section) and
confirmed the exact log line `No x-dcentb.asyncProcessing section in the OpenAPI document — async
task processing is disabled for this deployment` plus a completely normal startup — existing
deployments are unaffected by this feature existing in the codebase.

**Demo wiring, and a real end-to-end run, not just unit tests**: added `x-dcentb.asyncProcessing`
(defaults only) and one direct-attach topic (`students` → `StudentsAuditTaskProcessor`, a small
reference example that logs every write) to `demo.openapi.json`. Packaged the real executable jar,
ran it against the actual replica-set MongoDB, and confirmed the full path live:
- Startup log: `Async task processing enabled: ... streamId='default', db='dcentb-demo', 1 topic(s), watching collections [students]`, followed by `Acquired leadership of stream 'default' ... generation 1` and the cursor opening.
- `curl -X POST /students` (through the real HTTP layer, real `ApiKeyProvisioner`-issued admin key) → `201 Created`.
- **37 milliseconds later**, without any polling or manual trigger: `[students-audit] insert documentKey={"_id":"0c3ec58aaa664f9b8d779b45210b11d7"}` — the exact `_id` the REST response returned.
- `async-checkpoints` in MongoDB shows the persisted resume token and precise `clusterTime` for that event.
- Graceful shutdown (`kill`, not `kill -9`) logged `Released leadership of stream 'default'`, and the lease document's `leaderId` was confirmed `null` afterward — `AsyncProcessingLifecycle`'s `@PreDestroy` hook works.

No regressions: `MongoJsonDBIT` (10/10), `LeaderElectorIT` (8/8), `TaskProcessorResolverTest` (3/3),
`TopicRouterIT` (9/9), `ChangeStreamListenerIT` (3/3), `ControllerIT` (3/3, confirming the
`demo.openapi.json` edit didn't disturb existing REST behavior — `ControllerIT` constructs
`RequestHandlerProvider` directly per test case and never boots Spring, so it never touches
`AsyncProcessingAutoConfiguration` at all, by design).

Next: **phase 7, the background error-queue processor** remains the last piece from the original
9-phase plan (§9.3) not yet built.

## 16. Phase 7 status: done — all 9 original phases complete

`ErrorQueueProcessor`, plus new read/reschedule/dead-letter methods on `ErrorQueueStore`
(previously write-only) and a new `TopicRouter.redeliver(topic, subscriber, event)` that re-invokes
exactly one subscriber directly, bypassing collection-based routing, for items the processor already
knows the topic/subscriber for.

**Leader-gated without electing independently**: `ErrorQueueProcessor` only calls
`LeaderElector.isStillLeader()` (read-only) — it relies on the co-located `ChangeStreamListener`,
sharing the *same* `LeaderElector` instance, to actually renew the lease on a heartbeat. Two
components, one election — consistent with §2.1's "just one listener" answer, now extended to "just
one election" for everything running on a given streamId. Verified directly:
`ErrorQueueProcessorIT.doesNothingWithoutLeadership` constructs an elector that's never acquired and
confirms the processor sits idle — proving the no-independent-election claim isn't just asserted in
a docstring.

**`BLOCK_ALL` from a redelivered item is honestly scoped, not silently under-handled**: it halts the
error-queue processor's own loop (stops touching further items) but does not yet reach across to also
halt a co-located `ChangeStreamListener` — the same category of gap as `HaltListenerException` not
surviving a process restart. Flagged in `ErrorQueueProcessor`'s Javadoc.

**Backoff and dead-lettering, now actually consumed, so now actually parsed**: `AsyncProcessingSettings`
gained `errorQueue.pollIntervalSeconds`/`baseBackoffSeconds`/`maxBackoffSeconds` and
`deadLetterQueue.collection`/`maxErrorQueueRequeues` — deliberately *not* added back in phase 4-6
(§14) when `ErrorQueueStore` was write-only, exactly per that section's stated rule: parse a config
field only once the code that consumes it exists. `_id` on error-queue documents is an explicit
random UUID string (not an auto-generated ObjectId), matching the same "stay in the plain-JSON-string
convention already used throughout" reasoning as the leader-lease and checkpoint documents.

Verified, `ErrorQueueProcessorIT` (4 tests, real MongoDB): a succeeding redelivery removes the item;
repeated failures reschedule with backoff and dead-letter exactly at `maxErrorQueueRequeues` attempts
(with the dead-letter document carrying the original event and final failure reason); no leadership
→ no activity at all; `BLOCK_ALL` halts the processor without deleting or rescheduling the item.

**Live app verification, not just tests** — same rigor as §15: wired `ErrorQueueProcessor` into
`AsyncProcessingAutoConfiguration`/`AsyncProcessingLifecycle` (shares the listener's `LeaderElector`
and `TopicRouter`), rebuilt the real jar, ran it against the real replica-set MongoDB, and directly
seeded a document into `async-processing-errors` for the already-wired `students`/`audit` subscriber
via `mongosh`. Within one poll cycle: `StudentsAuditTaskProcessor` was invoked via redelivery
(`[students-audit] insert documentKey={"_id":"manual-test-doc"}`), logged
`Error-queue item for topic 'students' subscriber 'audit' succeeded on redelivery (attempt 1)`, and
the document count in `async-processing-errors` confirmed `0` afterward. Graceful shutdown again
released leadership cleanly.

No regressions: `MongoJsonDBIT` (10/10), `LeaderElectorIT` (8/8), `TaskProcessorResolverTest` (3/3),
`TopicRouterIT` (9/9), `ChangeStreamListenerIT` (3/3), `ErrorQueueProcessorIT` (4/4), `ControllerIT`
(3/3) — 30 tests total in `dcentb`.

**All 9 phases from §9.3's original build order are now complete.** What remains is exactly what's
been flagged along the way, not new scope: `BLOCK_ALL` not surviving a restart or coordinating across
the listener/error-queue-processor pair; the manual resume-timestamp override from §8.2; per-topic/
subscriber `maxRetries` overrides; the per-topic circuit breaker deferred in §10; and observability
(§9.3 step 8 — lag, checkpoint age, queue depth as actual metrics, not just log lines) was the one
step never separately picked up as its own phase.
