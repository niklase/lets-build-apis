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






















