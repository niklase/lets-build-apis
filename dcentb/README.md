# DcentB - a Data Centric Backend

> A declarative backend framework for Spring Boot — CRUD, validation, filtering and fine-grained authorization in configuration instead of boilerplate code.

# Why?

The goal of **dcentb** is to provide a good default implementation of a REST API for any type of data entity. A highly opinionated and consistent _API design system_ provides a good developer experience for your API consumers as well as for you as API provider. 



- OpenAPI-format as specification (and implementation!) of API operations including data input and data output
- JSON Schema to declare fine-grained access control of both input and output data
- Java Spring Boot Application with unlimited options for extensions

# What it looks like

Define your schema in OpenAPI once — dcentb serves the full CRUD API immediately.

**Create**
```http
POST /students
{
    "name": "Anna", 
    "email": "anna@school.com", 
    "grade": "A", 
    "attendancePercent": 92
}

201 Created
{
    "name": "Anna", 
    "email": "anna@school.com", 
    "grade": "A", 
    "attendancePercent": 92,
    "meta": {
        "id": "abc123", 
        "href": "/students/abc123", 
        "createdAt": "2024-09-01T09:00:00Z"
    }
}
```

Every item gets `meta.id`, `meta.href`, `meta.createdAt` and `meta.updatedAt` for free.

**Filter, paginate and sort — built in**
```http
GET /students?filter.teacherId.eq=teacher-A&limit=10&orderBy=name+desc

200 OK
{
    "items": [...], 
    "meta":{
        "size": 20, 
        "offset": 0, 
        "limit": 20
    }
}    
```

**Partial update and delete**
```http
PATCH /students/abc123   {"grade": "B"}   → 200
DELETE /students/abc123                   → 204
```

**Access control is a JSON Schema rule in the spec — not middleware, not annotations.** Declare which roles can access which operations and which response fields they can see. Rules are data-driven: a teacher can only query students assigned to them:

```json
{
  "permission": "teacher",
  "requestSchema": {
    "properties": { "query": {
      "required": ["filter.teacherId.eq"],
      "properties": { "filter.teacherId.eq": { "items": { "const": { "$data": "/authenticatedUser/teacherId" } } } }
    }}
  },
  "responseSchema": {
    "properties": { "body": { "properties": { "items": {
      "items": { "properties": {"name": true, "grade": true, "email": true}, "additionalProperties": false }
    }}}}
  }
}
```

A student can only update their own email. An admin sees everything. All of this lives in the OpenAPI file — no Java code needed.

---

# Add dcentb to an existing Spring Boot application

dcentb registers itself automatically via Spring Boot auto-configuration. Any route not handled by your own controllers is caught by dcentb and routed to MongoDB based on your OpenAPI spec.

#### 1. Build the project:

dcentb is still in development and is not yet available on Maven Central. Therefore you need to build the project first:

```
mvn -f ../pom.xml install -pl dcentb -am
```


#### 2. Add the dependency

```xml
<dependency>
    <groupId>com.zuunr</groupId>
    <artifactId>dcentb</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

#### 3. Add your OpenAPI spec

Place your spec on the classpath (e.g. `src/main/resources/my-api.openapi.json`).

#### 4. Configure `application.properties`

```properties
dcentb.openapi.file=classpath:my-api.openapi.json
dcentb.mongodb.connection=mongodb://admin:adminpassword@localhost:27017/?authSource=admin
dcentb.mongodb.db=my-database
```

That is all. dcentb now handles `POST`, `GET`, `PATCH`, and `DELETE` for any path defined in your spec. Routes defined in your own `@RestController` classes always take priority.

The Swagger UI is available at `http://localhost:8080/swagger`.

---

# Standalone Quickstart

#### 1. Build the project:

dcentb is still in development and is not yet available on Maven Central. Therefore you need to build the project first:


```
mvn -f ../pom.xml install -pl dcentb -am
```

#### 2. Start MongoDB:

MongoDB is run as a **single-node replica set** (`--replSet rs0`), not standalone. This is required for [MongoDB change streams](https://www.mongodb.com/docs/manual/changeStreams/), which dcentb's async task processing (see `docs/async-tasks-processing.md`) is built on — a standalone `mongod` cannot open a change stream at all. A single-node replica set behaves identically to standalone MongoDB for everything else (all existing CRUD/REST behavior is unaffected), so this is safe to use even if you don't need change streams yet.

Because authorization is enabled (`MONGO_INITDB_ROOT_USERNAME`/`PASSWORD`), `mongod` also requires an internal cluster authentication **keyFile** as soon as `--replSet` is used — even for a single node. Without it, the container exits immediately with `security.keyFile is required when authorization is enabled with replica sets`. Generate one once, in a small named volume owned by the image's `mongodb` user (uid `1000`):

```
docker volume create mongodb_keyfile
docker run --rm -v mongodb_keyfile:/keyfile alpine sh -c '
  apk add --no-cache openssl >/dev/null 2>&1
  openssl rand -base64 756 > /keyfile/mongo-keyfile
  chown 1000:1000 /keyfile/mongo-keyfile
  chmod 400 /keyfile/mongo-keyfile
'
```

```
docker run --name mongodb \
  -p 27017:27017 \
  -e MONGO_INITDB_ROOT_USERNAME=admin \
  -e MONGO_INITDB_ROOT_PASSWORD=adminpassword \
  -v mongodb_keyfile:/data/keyfile \
  -d mongodb/mongodb-community-server:latest \
  --replSet rs0 --keyFile /data/keyfile/mongo-keyfile
```

The container starts as an *uninitialized* replica set member. Initiate it once (only needed the first time a given data volume is created):

```
docker exec mongodb mongosh --quiet -u admin -p adminpassword --authenticationDatabase admin \
  --eval 'rs.initiate({_id: "rs0", members: [{_id: 0, host: "localhost:27017"}]})'
```

Check status any time with:

```
docker exec mongodb mongosh --quiet -u admin -p adminpassword --authenticationDatabase admin --eval 'rs.status().ok'
```

**If you already have an existing `mongodb` container running standalone** (started without `--replSet`), convert it in place without losing data — `--replSet` (and, as above, `--keyFile`) are `mongod` startup flags, so the container has to be recreated, but its data volumes (named or anonymous) carry across untouched as long as you don't pass `-v` to `docker rm`:

```
# capture the exact existing volume mounts (works whether they're named or anonymous)
DATA_VOL=$(docker inspect mongodb --format '{{range .Mounts}}{{if eq .Destination "/data/db"}}{{.Name}}{{end}}{{end}}')
CONFIG_VOL=$(docker inspect mongodb --format '{{range .Mounts}}{{if eq .Destination "/data/configdb"}}{{.Name}}{{end}}{{end}}')

docker stop mongodb
docker rm mongodb   # container only — no -v flag, so $DATA_VOL / $CONFIG_VOL and their data are kept

# generate the keyFile as shown above first if you haven't already (docker volume create mongodb_keyfile ...)

docker run --name mongodb \
  -p 27017:27017 \
  -e MONGO_INITDB_ROOT_USERNAME=admin \
  -e MONGO_INITDB_ROOT_PASSWORD=adminpassword \
  -v "$DATA_VOL":/data/db \
  -v "$CONFIG_VOL":/data/configdb \
  -v mongodb_keyfile:/data/keyfile \
  -d mongodb/mongodb-community-server:latest \
  --replSet rs0 --keyFile /data/keyfile/mongo-keyfile

docker exec mongodb mongosh --quiet -u admin -p adminpassword --authenticationDatabase admin \
  --eval 'rs.initiate({_id: "rs0", members: [{_id: 0, host: "localhost:27017"}]})'
```

#### 3. Run the demo backend:

```
java -jar target/dcentb-1.0-SNAPSHOT-exec.jar
```

The demo OpenAPI spec (`demo.openapi.json`) is used by default. Database is connected via ` mongodb://admin:adminpassword@localhost:27017/?authSource=admin`. 

To use your own spec:

```
java -jar target/dcentb-1.0-SNAPSHOT-exec.jar --dcentb.openapi.file=path/to/your.openapi.json
```

To override the MongoDB connection or database name at runtime:

```
java -jar target/dcentb-1.0-SNAPSHOT-exec.jar \
  '--dcentb.mongodb.connection=mongodb://admin:adminpassword@localhost:27017/?authSource=admin' \
  --dcentb.mongodb.db=my-database
```

The API is now available at `http://localhost:8080` and the Swagger UI at `http://localhost:8080/swagger`.

The demo also has async task processing enabled (`x-dcentb.asyncProcessing`/`topics` in
`demo.openapi.json`) — every write to `/students` is picked up via a MongoDB change stream and
logged by `StudentsAuditTaskProcessor` (look for `[students-audit]` in the console output a moment
after a `POST`/`PUT`/`PATCH`/`DELETE`). This is a working reference example, not just documentation —
see `docs/async-tasks-processing.md` for the full design and `docs/leader-election.md` /
`docs/change-stream-listener.md` for how it's built. Async processing only activates when
`x-dcentb.asyncProcessing` is present in the loaded OpenAPI document, so it's opt-in per deployment.

# Supported API operations

Supported operations are:

- Create ```POST /{item-type}```
- Read item ```GET /{item-type}/{id}``` and read collection of items ```GET /{item-type}?{query}``` (to follow OWASP recommendations and avoid PII - Personal Identifiable Information, in URL: ```POST /{item-type}/getCollection```)
- Update ```PATCH /{item-type}/{id}```

####  To be done
- Delete ```DELETE /{item-type}/{id}```

# The processing of a Create, Update an Delete (CUD) operations

CUD requests are handled by the CUDItemRequestHandler by executing the Processors below. Each processor reads and updates the requestContext.

![CUDItemRequestHandler Flow](./pics/CUDItemRequestHandler.svg)

## ApiKeyAuthenticator

Authenticates the user/client by looking up the api-key HTTP header. Response status 401 means the api-key is either not provided or is not valid.

## OASRequestDeserializer

Deserializes (header, path and query) parameters and the request body (if there is one) according to the OpeanAPI doucument

## UserInfoProvider

Looks up user information about the authenticated user/client like permissions and other attributes needed for authorization.

## RequestAccessController

Verifies that user with userInfo from UserInfoProvider is authorized to send the request.

## DatabaseCommandReadCreator

Creates a database command that later can be processed by DatabaseCommandRunner. Separation from the DatabaseCommandRunner is done to enable different implementations of DatabaseCommandRunner for different databases.

## DatabaseCommandRunner

Executes the database command and updates the requestContext with the result

## DatabaseCommandResponseVerifier

Verifies the database command execution worked or returns a 5xx response

## CurrentStateFromDatabaseApplier

Creates the current state from the database item returned by DatabaseCommandRunner

## CurrentStateAccessController

Verifies that user with userInfo from UserInfoProvider is authorized to write update the curren state according to the request (e.g POST or PATCH with request JSON body or DELETE).

## NewStateCreator

POST  - new state is JSON body decorations of that 
PATCH - new state is current state that is updated with the requests JSON body by applying JSON Merge Patch
DELETE - new state is null

## StateTransitionValidator

Validates a JSON schema of a model that contains currentState and newState. If validation fails a 409 response is created

## NewStateToDatabaseItemCreator

Creates the database item that should be persisted by the database

## DatabaseCUDItemCommandCreator

Creates a database command to write/delete the database item

## NewStateResponseCreator

Put the new state in the response or no body at all the new state is null (ie reult from DELETE)

## ResponseAccessController

Verifies that the user with userInfo is authorized to read the information that is contained in the response and filters averything else (and possibly changes the status code too accordingly)

---

# Internal API calls: SystemApiClient and SUPERUSER

Sometimes code running *inside* dcentb (a `TaskProcessor` reacting to a change stream, typically)
needs to call this same backend's own API — read another collection's item, or write one — without
a real HTTP round-trip and without needing real credentials or per-collection permission config for
an identity that isn't a real external caller. `SystemApiClient` does this by re-entering
`Controller.execute(...)` in-process (the exact same pipeline a real inbound HTTP request goes
through — auth, access control, Mongo, `ItemDecorator`, everything) as the built-in **`SUPERUSER`**
identity — authorized for anything any role could do via the API, on any collection, unfiltered.

`SystemApiClient` grants `SUPERUSER` via a top-level `"internalPrincipal"` key on the request
`JsonObject` — a sibling of `"headers"`/`"body"`, never a header value:

```java
JsonObject requestObject = JsonObject.EMPTY
        .put("method", method)
        .put("uri", uri)
        .put("headers", headers)
        .put(Processor.INTERNAL_PRINCIPAL, "SYSTEM"); // "internalPrincipal"
```

`AuthenticationProcessor` recognizes that key and skips every header-based authenticator entirely,
setting `authenticatedUser.permissions = ["SUPERUSER"]`. `PreOperationAccessController` short-circuits
to `UNRESTRICTED_ACCESS` ("unrestrictedAccess") the moment it sees `SUPERUSER` in the permission list —
authorized unconditionally, on every collection, with **no collection ever declaring a `SUPERUSER`
entry in its own `x-dcentb.collections.*.permissions`**; it's a core dcentb capability, not per-API
config. `ResponseAccessController` checks the same flag and, when set, skips response filtering
entirely — `SUPERUSER` always gets the full, unfiltered, decorated item.

**This is a real security boundary, not an obfuscated secret.** `RequestUtil.createRequest` — the
only code path from a real inbound HTTP request to a `Request` object — only ever populates
`method`/`uri`/`headers`/`query`/`body`. It never sets `internalPrincipal`, so no external HTTP
request can ever trigger `SUPERUSER`, regardless of what headers or body it sends.

Getting it: a Spring-managed bean (a `TaskProcessor` annotated `@Component`, or any other Spring
bean) gets it via normal constructor injection; a reflectively-constructed (non-Spring) one reaches
it via `DcentbApplicationContextHolder.get().getBean(SystemApiClient.class)`.

**Recursion caveat**: the call is synchronous on the calling thread — a `TaskProcessor` must not
trigger an operation that re-invokes itself (directly or indirectly); there's no call-depth guard,
same as any recursive function call. It is also not part of any transaction with the outer
change-stream event — dcentb has no cross-request transaction concept today.

---

# Idempotency and etags on writes (PUT/POST/PATCH/DELETE)

Every item carries `meta.etag`, set fresh on every write. The Mongo write itself is optimistic-
concurrency-controlled *for some verbs, not all* — documented here rather than only in code comments.

### What each verb's write actually guards against a stale read

| Verb | `meta.etag` on write | Mongo write query | Stale-read outcome |
|---|---|---|---|
| **PATCH** | Kept from `currentState` (merge doesn't touch it) | `_id == X AND meta.etag == <etag read at start of request>` (`DatabaseCUDItemCommandCreator`) | **Fails closed.** If the real document moved on, this matches nothing; `upsert:true` then tries to insert a document whose `_id` already exists → Mongo duplicate-key error (`code 11000`) → `DatabaseCommandResponseVerifier` turns that into **`409 Conflict`**. A stale `currentState` produces a rejected write, never silent corruption. |
| **POST** | Freshly minted (`NewStateCreator`) | `_id == <new random id>`, no `meta.etag` condition | Not applicable — always a new document; nothing to be stale against. |
| **PUT, body differs from `currentState`** | *(never reaches a write — see below)* | *(never reaches a write)* | **Fails closed.** `StateTransitionValidator` compares `currentState` to `newState` (ignoring `meta`) *before* `IdempotentPutResponseCreator` ever runs; a mismatch is rejected as **`409 Conflict`** unconditionally — PUT never silently overwrites an existing item with a different body. |
| **PUT, body matches `currentState`** | Freshly minted, then **discarded** | *(no write at all)* | `IdempotentPutResponseCreator` short-circuits with `200 currentState` — correct by construction, since `StateTransitionValidator` just confirmed the body already matches what's stored. |
| **DELETE** | n/a | `_id == X AND meta.etag == currentState.etag` (`DatabaseCUDItemCommandCreator`) | **Fails closed.** Matching zero documents (the item was modified or deleted after this request read it) → `DatabaseCommandResponseVerifier` returns **`409 Conflict`** instead of silently no-op-succeeding with `204`. |

**Correction to an earlier version of this section**: `IdempotentPutResponseCreator` does *not* blindly
return `200` for any PUT into an existing item regardless of body — that earlier read of the code
missed that `StateTransitionValidator` (which runs first) already does the real check. For PUT, it
strips `meta` from both `currentState` and `newState` and compares them; a mismatch produces `409`
itself, via the same schema-violation response shape every other validation failure in this pipeline
uses. Only when they're equal does the request ever reach `IdempotentPutResponseCreator` — so
returning `currentState` as the `200` body is reporting exactly what the client just asked to
(re-)create, not stale or unrelated data. `IdempotentPutResponseCreator`'s own Javadoc now says this
explicitly. Functionally, PUT in this codebase means "create-if-absent; if it already exists with the
same body, hand back what's there (200); if it exists with a different body, reject (409)" — never a
blind replace.

DELETE's optimistic-concurrency guard (the last table row) is a change made alongside this
documentation, not pre-existing — see "Implemented" below.

### Implemented

- **`meta.etag` duplication in `NewStateCreator`** — consolidated. PUT and POST both call a single
  private `withFreshMeta(body, itemId, href)` helper instead of each inlining an identical
  `createdAt`/`updatedAt`/`etag` block.
- **DELETE now has an optimistic-concurrency guard**, matching the table above: its Mongo command
  conditions on `_id == itemId AND meta.etag == currentState.etag` (`DatabaseCUDItemCommandCreator`),
  and `DatabaseCommandResponseVerifier` now treats a delete that matched zero documents as a `409`
  (previously: a silent `204` no-op, indistinguishable from a real deletion). Covered by
  `DatabaseCUDItemCommandCreatorTest` (the command shape) and
  `DatabaseCommandResponseVerifierTest` (the zero-matches-→-409 behavior) — the actual race this
  guards against (the document changing in the narrow window between this request's own read and its
  own write) isn't reproducible through `ControllerIT`'s sequential given/when/then format, so it's
  unit-tested at the mechanism level rather than end-to-end; `idempotency-and-etag-test.json` covers
  the DELETE happy path (still `204`) and the already-gone path (still `404`) to prove the guard is a
  pure addition, not a behavior change, for every case that format *can* exercise.
- ~~PUT-into-an-existing-item never actually re-validates the body~~ — turned out to already be false;
  see the correction above. No code change was needed here, only the documentation.

All of the above, plus the pre-existing PUT/PATCH/POST/DELETE behavior, is exercised end-to-end in
`src/test/resources/.../ControllerIT/idempotency-and-etag-test.json`.

---

# ItemDecorator: decorating currentState and newState

An `ItemDecorator` post-processes an item on its way in or out — computing a derived field, embedding
data from another collection, normalizing input — without touching the CUD/read pipeline itself.

### The idea

`CurrentStateItemDecorator` and `NewStateItemDecorator` are both wired into `CUDItemRequestHandler`
(and `ReadItemRequestHandler` reuses the current-state half). Each is a thin adapter
(`ItemDecoratorProcessor`) that:

1. Resolves your app's decorator class **by convention**, once per operation:
   `x-dcentb.decoratorBasePackage + ".collections." + <collection name, "/"→".", "-"→"_"> + ".ItemDecorator"`
   — e.g. `com.zuunr.dcentb.demo` + `.collections.` + `students` → `com.zuunr.dcentb.demo.collections.students.ItemDecorator`.
2. Copies whichever state it's responsible for (`currentState` or `newState`) into a neutral
   `itemState` key, and calls your decorator with *only* that key visible — **your decorator never
   knows whether it's decorating currentState or newState**, so the same class handles both.
3. Writes `itemState` back onto the state it was copying from.

**Writing one is entirely optional.** No `decoratorBasePackage` configured, or no class at the
derived name for a collection → silent no-op passthrough for that collection. A class that *does*
exist but is shaped wrong (no `(JsonValue)` constructor, doesn't extend `Processor`) fails loudly —
"optional" only covers "no one wrote one," not bugs in the one that was written.

### How to configure it

```jsonc
{
  "x-dcentb": {
    "decoratorBasePackage": "com.zuunr.dcentb.demo"
  }
}
```

### How to code one

A plain `Processor` that reads/writes only `"itemState"`:

```java
public class ItemDecorator extends Processor {

    public ItemDecorator(JsonValue config) {
        super(config);
    }

    @Override
    public JsonObject process(JsonObject requestContext) {
        JsonObject itemState = requestContext.get("itemState", JsonValue.NULL).getJsonObject();

        // derive a field
        JsonValue attendancePercent = itemState.get("attendancePercent");
        if (attendancePercent != null && attendancePercent.isJsonNumber()) {
            String status = attendancePercent.getInteger() < 60 ? "AT_RISK" : "OK";
            itemState = itemState.put("attendanceStatus", status);
        }

        return requestContext.put("itemState", itemState);
    }
}
```

The real demo decorator (`demo/collections/students/ItemDecorator.java`) also embeds the full
`teachers/{id}` item referenced by `teacherId`, so `students`' `stateTransitionSchema` can require
`newState.teacher.status` — enforcing, declaratively, that the referenced teacher exists before a
student write is accepted, instead of as a database foreign-key constraint.

**Design note — why this decorator reads Mongo directly instead of via `SystemApiClient`**: an
`ItemDecorator` is resolved reflectively *per operation*, including inside
test harnesses (`ControllerIT`) that construct `RequestHandlerProvider` directly and never boot
Spring — so no `SystemApiClient` bean exists to inject there. A plain by-id read of a collection with
no `ItemDecorator`/write-side validation of its own returns identical content either way, so going
straight to `MongoJsonDB` (the same escape hatch `ApiKeyAuthenticator` already uses) is a deliberate,
narrower choice than "always go through the API" — appropriate for a **read-only, same-request**
lookup. Contrast this with `ClassSummarySyncTaskProcessor` below, which *writes* and only ever runs
inside a real Spring-booted app — that's where going through the REST API (and `SUPERUSER`) actually
matters. A Spring-managed decorator (annotated `@Component`) can still use normal
`@Autowired`/constructor injection when it needs `SystemApiClient` or anything else.

---

# Async task processing: change streams, leader election, and TaskProcessors

dcentb can react to its own writes: every CUD operation that hits MongoDB is observable via a
**change stream**, routed to your own **`TaskProcessor`** business logic — a fan-out/pub-sub system
built entirely on MongoDB and Spring Boot, no external broker. This is a full feature with its own
design docs; this section is the concept map and configuration/coding reference. Full depth:

- `docs/async-tasks-processing.md` — the complete design, all decisions and their reasoning, and the
  phase-by-phase build history.
- `docs/leader-election.md` — the fencing-token protocol one instance uses to safely be "the one"
  processing a stream, with automatic failover.
- `docs/change-stream-listener.md` — how the single database-level change stream is consumed,
  checkpointed, and kept from observing its own bookkeeping writes.

### The idea

```
MongoDB write (POST/PUT/PATCH/DELETE)
        │  (observed via a single db.watch() change stream, leader-instance only)
        ▼
  ChangeStreamListener  ──routes by source collection──▶  Topic
                                                              │
                                          1 subscriber ───────┼─────── 2+ subscribers
                                          (direct-attach)     │        (fan-out-on-read:
                                                               │        1 message-log write,
                                                               ▼        every subscriber reads it)
                                                        TaskProcessor.process(event)
                                                               │
                                                   SUCCESS / RETRY / ERROR_QUEUE / BLOCK_ALL
```

- **One listener for the whole app** (a single `db.watch()`), started only on the currently-elected
  leader — see `docs/leader-election.md` for the CAS/fencing-token protocol that makes failover safe
  (a paused-then-resumed ex-leader is fenced out, never allowed to act as leader again).
- **Topics** map a source collection to one or more named **subscribers**. Fan-out is derived from
  subscriber *count*, not a separate flag: exactly one subscriber attaches directly to the raw event
  (no extra write); two or more automatically get a shared message-log write plus lockstep dispatch
  to every subscriber — no per-subscriber offset bookkeeping is needed, because one listener drives
  all subscribers of a topic synchronously within the same cursor advance.
- **At-least-once delivery, checkpoint-after-processing.** A crash between "processed" and
  "checkpoint written" causes reprocessing, not data loss — which is exactly why:
- **Every `TaskProcessor` must be idempotent.** The same event can reach `process()` more than once
  (a crash, an explicit `RETRY`, redelivery from the error queue).

### `TaskResult` — four outcomes, not a boolean

```java
public interface TaskProcessor {
    TaskResult process(JsonObject event);
}
```

| Outcome | Meaning | Blast radius |
|---|---|---|
| `TaskResult.success()` | Done. | — |
| `TaskResult.retry(reason)` | Transient failure. | Exactly **one** immediate inline retry; a second failure escalates to `ERROR_QUEUE` automatically — final, not a default you can override. |
| `TaskResult.errorQueue(reason)` | Give up on this one item, for this one subscriber. | Checkpoint still advances — a struggling subscriber never blocks anything else. Retried later with backoff by `ErrorQueueProcessor`; dead-lettered after `maxErrorQueueRequeues`. |
| `TaskResult.blockAll(reason)` | Something is wrong enough that processing must stop. | Halts the **entire** listener instance (thrown as `HaltListenerException`) — not just this topic. Does not currently survive a process restart; a restarted instance re-elects and will likely hit the same failure again (safe — never silently resumes — but not yet a persisted "stay blocked" mechanism). |

An uncaught exception or a `null` return from `process()` is treated identically to an explicit
`retry(...)`.

### How to configure topics and subscribers

Lives inside the one loaded OpenAPI document, under `x-dcentb`. From `demo.openapi.json`:

```jsonc
{
  "x-dcentb": {
    "asyncProcessing": { "streamId": "default" },
    "topics": [
      {
        "name": "students",
        "sourceCollection": "students",
        "subscribers": [
          {
            "name": "audit",
            "taskProcessorClass": "com.zuunr.dcentb.demo.collections.students.taskprocessors.StudentsAuditTaskProcessor"
          },
          {
            "name": "classSummarySync",
            "taskProcessorClass": "com.zuunr.dcentb.demo.collections.students.taskprocessors.ClassSummarySyncTaskProcessor"
          }
        ]
      }
    ]
  }
}
```

Two subscribers on `students` means this topic is fan-out-on-read: a shared message-log document is
written per event, and both `StudentsAuditTaskProcessor` and `ClassSummarySyncTaskProcessor` are
dispatched to it. If `x-dcentb.asyncProcessing` is absent from the loaded document entirely, async
processing is disabled for that deployment — no MongoDB connection is even opened for it.

`taskProcessorClass` is always named explicitly per subscriber (unlike `ItemDecorator`, there's no
base-package-plus-naming-convention to derive from — see `TaskProcessorResolver`'s Javadoc for why: a
collection can have zero, one, or many `TaskProcessor`s, so the wiring has to be explicit and listed).
A missing/wrong class is a real configuration error and throws at startup, rather than `ItemDecorator`'s
"optional, silently no-op" behavior.

### How to code one

Resolution mirrors `ItemDecoratorProcessor`: a Spring-managed bean (`@Component`, normal
`@Autowired`/constructor injection) is preferred, falling back to reflective construction via a
`(JsonValue)` constructor.

**Simple case — no cross-collection effects, just react**, reflectively constructed:

```java
public class StudentsAuditTaskProcessor implements TaskProcessor {

    public StudentsAuditTaskProcessor(JsonValue config) {
        // (JsonValue) constructor required by the reflective-construction fallback
    }

    @Override
    public TaskResult process(JsonObject event) {
        String operationType = event.get("operationType").getString();
        JsonValue documentKey = event.get("documentKey");
        LOG.info("[students-audit] {} documentKey={}", operationType, documentKey);
        return TaskResult.success();
    }
}
```

**Cross-collection case — keeping another document eventually consistent**, Spring-managed so it can
inject `SystemApiClient`, and calling **through the REST API as `SUPERUSER`** rather than writing
Mongo directly — this is the case where going through the API (see the section above) actually
matters, because it re-runs that other collection's own `ItemDecorator`/`stateTransitionSchema`/
access-control instead of bypassing them:

```java
@Component
public class ClassSummarySyncTaskProcessor implements TaskProcessor {

    private final SystemApiClient systemApiClient;

    public ClassSummarySyncTaskProcessor(SystemApiClient systemApiClient) {
        this.systemApiClient = systemApiClient;
    }

    @Override
    public TaskResult process(JsonObject event) {
        JsonValue fullDocument = event.get("fullDocument");
        if (fullDocument == null || !fullDocument.isJsonObject()) {
            return TaskResult.success(); // e.g. a delete — see the class's own Javadoc for the known gap
        }
        String teacherId = fullDocument.getJsonObject().get("teacherId").getString();

        Response<?> studentsResponse = systemApiClient.call("POST", "/students/getCollection",
                JsonObject.EMPTY.put("filter", JsonObject.EMPTY
                        .put("teacherId", JsonObject.EMPTY.put("eq", teacherId))).jsonValue());
        if (studentsResponse.getStatus() != 200) {
            return TaskResult.retry("POST /students/getCollection returned " + studentsResponse.getStatus());
        }

        // ...recompute classSummary from studentsResponse.getBody()...

        Response<?> patchResponse = systemApiClient.call("PATCH", "/teachers/" + teacherId,
                classSummary.jsonValue());
        if (patchResponse.getStatus() != 200) {
            return TaskResult.retry("PATCH /teachers/" + teacherId + " returned " + patchResponse.getStatus());
        }
        return TaskResult.success();
    }
}
```

Idempotent by construction: every invocation **recomputes `classSummary` from scratch** (a fresh
query over the teacher's current students) and PATCHes the full, current result — never an
increment/decrement — so redelivering the same event twice produces the same end state, satisfying
the at-least-once contract every `TaskProcessor` must tolerate.

`SystemApiClient.call` needs no credentials — see "Internal API calls" above for exactly how
`SUPERUSER` is granted and why it can never be triggered from outside the process.






















