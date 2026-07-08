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
    "grade": "A", "attendancePercent": 92,
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

#### 1. Add the dependency

```xml
<dependency>
    <groupId>com.zuunr</groupId>
    <artifactId>dcentb</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

#### 2. Add your OpenAPI spec

Place your spec on the classpath (e.g. `src/main/resources/my-api.openapi.json`).

#### 3. Configure `application.properties`

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

```
mvn -f ../pom.xml install -pl dcentb -am
```

#### 2. Start MongoDB:

```
docker run --name mongodb \
  -p 27017:27017 \
  -e MONGO_INITDB_ROOT_USERNAME=admin \
  -e MONGO_INITDB_ROOT_PASSWORD=adminpassword \
  -d mongodb/mongodb-community-server:latest
```

#### 3. Run the demo backend:

```
java -jar target/dcentb-1.0-SNAPSHOT-exec.jar
```

The demo OpenAPI spec (`demo.openapi.json`) is used by default and already configures the MongoDB database name via `x-dcentb.mongodb.db`. To use your own spec:

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

# Supported API operations

Supported operations are:

- Create ```POST /{item-type}```
- Read item ```GET /{item-type}/{id}``` and read collection of items ```GET /{item-type}?{query}``` (to follow OWASP recommendations and avoid PII - Personal Identifiable Information, in URL: ```POST /{item-type}/getCollection```)
- Update ```PATCH /{item-type}/{id}```

####  To be done
- Delete ```DELETE /{item-type}/{id}``` 

