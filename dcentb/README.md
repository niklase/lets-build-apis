# dcentb - A Data Centric Backend

**dcentb** is a declarative REST API backend that integrates with MongoDB.

# Why?

The goal of **dcentb** is to provide a good default implementation of a REST API for any data entities. A highly opinionated and consistent API design system provides a good developer experience for your API consumers as well as for you as     API provider. 

- OpenAPI-format rules API operations including data input and data output
- JSON Schema to declare fine-grained access control of both input and output data
- Java Spring Boot Application with unlimited options for extensions

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

