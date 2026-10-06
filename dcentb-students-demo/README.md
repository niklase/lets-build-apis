# dcentb Students Demo

A students/teachers REST API demonstrating dcentb's `ItemDecorator`, `TaskProcessor`, and async
change-stream processing features — extracted into its own module so `dcentb` itself carries no
API-specific business logic or API definitions. See `dcentb`'s own README for the generic framework
mechanics this module is an example *of*; this README is about what's specific to this app.

---

## What you get

| Endpoint | Description |
|---|---|
| `POST /students` | Create a student |
| `GET /students` / `GET /students/getCollection` | List students (filter, paginate, sort) |
| `GET /students/{id}`, `PATCH /students/{id}`, `PUT /students/{id}`, `DELETE /students/{id}` | Read/update/replace/delete one student |
| `POST /teachers`, `GET /teachers/{id}`, `PATCH /teachers/{id}` | Manage teachers |
| `GET /swagger` | Swagger UI |

A student's `attendanceStatus` is derived automatically, its `email` is lowercased, and its
referenced `teacher` is embedded and validated to exist — all via `ItemDecorator`, no controller
code. Every write to `/students` is also picked up asynchronously via a MongoDB change stream and:
logged (`StudentsAuditTaskProcessor`), and used to keep the referenced teacher's `classSummary`
(student count, at-risk count, average attendance) eventually consistent
(`ClassSummarySyncTaskProcessor`) — purely by calling this same app's own REST API as `SUPERUSER`,
never touching MongoDB directly.

---

## Prerequisites

- Java 17+
- Maven
- MongoDB, run as a **single-node replica set** (required for change streams — a standalone
  `mongod` cannot open one at all). See `dcentb`'s own README, "Standalone Quickstart" → "Start
  MongoDB", for the full setup (keyFile generation, `rs.initiate()`, and how to convert an existing
  standalone container in place without losing data). The short version, for a fresh setup:

  ```bash
  docker volume create mongodb_keyfile
  docker run --rm -v mongodb_keyfile:/keyfile alpine sh -c '
    apk add --no-cache openssl >/dev/null 2>&1
    openssl rand -base64 756 > /keyfile/mongo-keyfile
    chown 1000:1000 /keyfile/mongo-keyfile
    chmod 400 /keyfile/mongo-keyfile
  '
  docker run --name mongodb -p 27017:27017 \
    -e MONGO_INITDB_ROOT_USERNAME=admin -e MONGO_INITDB_ROOT_PASSWORD=adminpassword \
    -v mongodb_keyfile:/data/keyfile \
    -d mongodb/mongodb-community-server:latest \
    --replSet rs0 --keyFile /data/keyfile/mongo-keyfile

  docker exec mongodb mongosh --quiet -u admin -p adminpassword --authenticationDatabase admin \
    --eval 'rs.initiate({_id: "rs0", members: [{_id: 0, host: "localhost:27017"}]})'
  ```
- dcentb built locally (see below).

---

## Quickstart

```bash
mvn -f ../pom.xml install -pl dcentb -am
mvn package
java -jar target/dcentb-students-demo-1.0-SNAPSHOT.jar
```

On first boot, look for a line like this in the console for each pre-configured user in
`x-dcentb.apiKeyGeneration` (`admin-a1`, `teacher-A`, `teacher-B`, `student-anna`, ...) — the key is
a freshly generated UUID, not a fixed string, so it's only ever available this way (or by reading
the `api-keys` collection directly):

```
[dcentb] api-key generated  userId=admin-a1  key=3f8e2c9a-...  document={...}
```

Open the Swagger UI at **http://localhost:8080/swagger**, or create a teacher and a student
directly using that key:

```bash
ADMIN_KEY=<the key printed for userId=admin-a1>

curl -X POST localhost:8080/teachers -H 'content-type: application/json' \
  -H "api-key: $ADMIN_KEY" \
  -d '{"teacherId": "teacher-A", "name": "Teacher A", "status": "ACTIVE"}'

curl -X POST localhost:8080/students -H 'content-type: application/json' \
  -H "api-key: $ADMIN_KEY" \
  -d '{"studentId": "student-anna", "teacherId": "teacher-A", "name": "Anna", "email": "ANNA@example.com", "grade": "A", "attendancePercent": 92}'
```

A moment later, check the console log for `[students-audit] insert ...` and `[classSummarySync]
teacherId=teacher-A studentCount=1 ...` — proof the change-stream pipeline (leader election →
change stream → topic routing → `TaskProcessor`) is really running, not just configured.

---

## Access control

Three roles, configured entirely in `students-demo.openapi.json` — no Java code:

| Role/Permission | Can do |
|---|---|
| `admin` | Everything |
| `teacher` | Update `grade` and `attendancePercent` of their own students (`teacherId` must match their own, enforced via `{"$data": "/authenticatedUser/teacherId"}`) |
| `student` | Update their own `email` |

API keys are seeded from `x-dcentb.apiKeyGeneration` on first run if not already present in the
`api-keys` collection.

---

## How the async pipeline is wired

`students-demo.openapi.json`'s `x-dcentb.asyncProcessing`/`topics`:

```jsonc
{
  "x-dcentb": {
    "asyncProcessing": { "streamId": "default" },
    "topics": [
      {
        "name": "students",
        "sourceCollection": "students",
        "subscribers": [
          { "name": "audit", "taskProcessorClass": "com.zuunr.demo.students.collections.students.taskprocessors.StudentsAuditTaskProcessor" },
          { "name": "classSummarySync", "taskProcessorClass": "com.zuunr.demo.students.collections.students.taskprocessors.ClassSummarySyncTaskProcessor" }
        ]
      }
    ]
  }
}
```

Two subscribers means this topic is fan-out-on-read (a shared message-log write, both subscribers
dispatched to it) rather than direct-attach — see `dcentb`'s own README, "Async task processing,"
for what that means and why. `ClassSummarySyncTaskProcessor` is the interesting one: it's
`@Component`-annotated (Spring-managed, so it can have `SystemApiClient` injected) and calls back
into this same app's own `/students/getCollection` and `/teachers/{id}` endpoints **as `SUPERUSER`**
rather than writing MongoDB directly — which means it re-runs `ItemDecorator`/access-control on
`teachers` exactly like any other caller would, instead of bypassing them. See its own Javadoc for
one known, deliberate limitation (a DELETE event never carries the deleted document, so a deleted
student's teacher isn't recomputed until another event for that teacher happens to trigger it).

---

## Testing

`ControllerIT` (this module's own, built on the exact same `DcentbGivenWhenThenTester`/
`OpenApiExampleAugmenter` harness `dcentb`'s own `ControllerIT` uses, reused here via `dcentb`'s
test-jar rather than duplicated) runs the JSON fixtures under
`src/test/resources/com/zuunr/demo/students/ControllerIT/` — full CRUD, access-control, and
idempotency/etag coverage against this real app. `ClassSummarySyncTaskProcessorIT` drives
`ClassSummarySyncTaskProcessor.process(...)` directly with a hand-built change-stream-shaped event
(no real leader election or change stream involved — that machinery has its own dedicated test
suite in `dcentb` itself), asserting on the processor's own recompute logic.

---

## See also

- `dcentb`'s own README — the framework mechanics (CUD pipeline, `ItemDecorator` resolution,
  `SystemApiClient`/`SUPERUSER`, idempotency/etags) this app is an example of.
- `dcentb`'s `docs/async-tasks-processing.md`, `docs/leader-election.md`,
  `docs/change-stream-listener.md` — the full async-processing design.
