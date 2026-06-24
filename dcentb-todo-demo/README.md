# dcentb Todo Demo

A complete todo list REST API built with **zero Java business logic** — just one Spring Boot entry point and an OpenAPI spec.

This project is a tutorial for how dcentb turns an OpenAPI document into a running CRUD backend backed by MongoDB.

---

## What you get

| Endpoint | Description |
|---|---|
| `POST /todos` | Create a todo item |
| `GET /todos` | List all todos (supports `limit`, `offset`, `filter.done.eq`) |
| `GET /todos/{id}` | Fetch one todo by id |
| `GET /swagger` | Swagger UI — try the API interactively |

---

## Prerequisites

- Java 17+
- Maven
- MongoDB (see below)
- dcentb built locally (see below)

---

## Quickstart

#### 1. Start MongoDB

```bash
docker run --name mongodb \
  -p 27017:27017 \
  -e MONGO_INITDB_ROOT_USERNAME=admin \
  -e MONGO_INITDB_ROOT_PASSWORD=adminpassword \
  -d mongodb/mongodb-community-server:latest
```

#### 2. Build dcentb

dcentb is not yet published to Maven Central, so build it locally first:

```bash
mvn -f ../pom.xml install -pl dcentb -am
```

#### 3. Build and run this demo

```bash
mvn package
java -jar target/dcentb-todo-demo-1.0-SNAPSHOT.jar
```

#### 4. Try the API

Open the Swagger UI at **http://localhost:8080/swagger**.

Click **Authorize** and enter one of the pre-configured API keys:

| API key | User |
|---|---|
| `demo-secret_alice_user` | alice |
| `demo-secret_bob_user` | bob |

Then try `POST /todos` with:
```json
{
  "title": "Buy oat milk",
  "done": false
}
```

The response includes a `meta.id` you can use to fetch the item with `GET /todos/{id}`.

---

## How it works

Everything is driven by `todo-api.openapi.json`. No controller, no repository, no service class — dcentb reads the spec at startup and wires the HTTP layer to MongoDB automatically.

### The three moving parts

#### 1. MongoDB connection — `application.properties`

```properties
dcentb.openapi.file=classpath:todo-api.openapi.json
dcentb.mongodb.connection=mongodb://admin:adminpassword@localhost:27017/?authSource=admin
dcentb.mongodb.db=todo-demo
```

#### 2. Global config — top-level `x-dcentb` in the spec

```json
"x-dcentb": {
  "mongodb": {
    "db": "todo-demo"
  },
  "accessControl": {
    "apiKeys": {
      "demo-secret_alice_user": {},
      "demo-secret_bob_user": {}
    }
  }
}
```

API keys follow the format `<secret>_<userId>_<role1>-<role2>`. dcentb parses the key on every request and makes `userId` and `permissions` available to the access control layer.

#### 3. Per-operation access control — `x-dcentb` on each path/method

```json
"x-dcentb": {
  "accessControl": {
    "permissionSchemas": [
      {
        "permission": "user",
        "requestSchema": {},
        "responseSchema": true
      }
    ]
  }
}
```

`permissionSchemas` is a list: dcentb checks whether the authenticated user's permissions match any entry. `requestSchema` is a JSON Schema that must validate against the incoming request context — `{}` means "allow any request". `responseSchema` controls which fields the user may see in the response — `true` means "return everything".

#### 4. Collection name is derived from the path

`/todos` → MongoDB collection `todos`. No explicit mapping needed.

#### 5. `meta` is auto-populated

Every item written via `POST` gets `meta.id` (UUID), `meta.href`, `meta.createdAt`, and `meta.updatedAt` set by dcentb. The `id` is what you use in `GET /todos/{id}`.

---

## What is good about this approach

**No boilerplate.** A typical Spring Boot CRUD endpoint requires a controller, a repository, a service, and often a DTO. Here the entire API surface is defined in one JSON file.

**OpenAPI is the single source of truth.** The spec is both documentation and runtime configuration. Swagger UI is served from the same spec at `/swagger`.

**Fine-grained access control without code.** `permissionSchemas` lets you restrict which fields a user can read or write using standard JSON Schema, including dynamic checks like `{"const": {"$data": "/authenticatedUser/userId"}}` to enforce ownership. This scales from "allow everything" to field-level row-level security without writing an access-control layer.

**Multiple user roles from one spec.** Adding a second `permissionSchemas` entry with a different `permission` value lets you describe what admins see vs. what regular users see — all in the spec.

**MongoDB collection naming is automatic.** `/todos` maps to the `todos` collection, `/orders/{id}` maps to `orders`. You can override with `"x-dcentb": {"mongodb": {"collection": "my_col"}}` on the operation.

---

## What can be cumbersome

**The API key format is non-standard and must be memorised.** The convention `<secret>_<userId>_<role1>-<role2>` is specific to dcentb. There is no validation feedback if you get the format wrong — the key simply fails authentication.

**`permissionSchemas` is verbose even for the simplest case.** Allowing all authenticated users requires four lines of JSON (`permission`, `requestSchema`, `responseSchema`, and the wrapping object). The `AUTHENTICATED_DEFAULT` permission shortcut helps but is not obvious from the spec alone.

**No schema validation of `x-dcentb` blocks.** If you mistype `permissionSchemas` as `permissionSchema`, dcentb silently denies all requests with 403. An IDE plugin or JSON Schema for `x-dcentb` does not yet exist.

**Restart required on spec changes.** The OpenAPI file is read once at startup. Changing access control rules or adding an endpoint requires restarting the application.

**The `meta` object shape must be known in advance.** Items are returned with a `meta` block containing `id`, `href`, `createdAt`, and `updatedAt`. If your response schema uses `additionalProperties: false`, you must explicitly allow `meta` or items will be stripped from responses.

---

## Customising the demo

To add a `PATCH /todos/{id}` (update) or `DELETE /todos/{id}` endpoint, add the path and method to the spec with the same `x-dcentb.accessControl.permissionSchemas` pattern — no Java code needed.

To restrict `POST /todos` so a user can only create todos for themselves, change `requestSchema` to:

```json
"requestSchema": {
  "properties": {
    "request": {
      "properties": {
        "body": {
          "properties": {
            "userId": {
              "const": { "$data": "/authenticatedUser/userId" }
            }
          }
        }
      }
    }
  }
}
```

This is standard JSON Schema with a `$data` pointer to the authenticated user — no code, just configuration.