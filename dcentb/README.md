# dcentb - A Data Centric Backend

**dcentb** is a declarative REST API backend that integrates with MongoDB.

# Why?

The goal of **dcentb** is to provide a good default implementation of a REST API for any data entities. A highly opinionated and consistent API design system provides a good developer experience for your API consumers as well as for you as     API provider. 

- OpenAPI-format rules API operations including data input and data output
- JSON Schema to declare fine-grained access control of both input and output data
- MongoDB connection string and database name
- Java Spring Boot Application with unlimited options for extensions

# Quickstart

#### 1. Build the project:

```
mvn -f ../../pom.xml install -pl public/dcentb -am
```

#### 2. Start MongoDB:

```
docker run --name mongodb -p 27017:27017 -d mongodb/mongodb-community-server:latest
```

#### 3. Run the demo backend:

```
java -jar target/dcentb-1.0-SNAPSHOT.jar
```

The demo OpenAPI spec (`demo.openapi.json`) is used by default and already configures the MongoDB database name via `x-dcentb.mongodb.db`. To use your own spec:

```
java -jar target/dcentb-1.0-SNAPSHOT.jar --dcentb.openapi.file=path/to/your.openapi.json
```

To override the MongoDB connection or database name at runtime:

```
java -jar target/dcentb-1.0-SNAPSHOT.jar \
  --dcentb.mongodb.connection=mongodb://user:pass@host:27017/?authSource=admin \
  --dcentb.mongodb.db=my-database
```

The API is now available at `http://localhost:8080` and the Swagger UI at `http://localhost:8080/swagger`.

# Supported API operations

Supported operations are:

- Create ```POST /{item-type}```
- Read item ```GET /{item-type}/{id}``` and read collection of items ```GET /{item-type}?{query}``` (to follow OWASP recommendations and avoid PII - Personal Identifiable Information, in URL: ```POST /{item-type}/getCollection```)
- Update ```PATCH /{item-type}/{id}```
- Delete ```DELETE /{item-type}/{id}```

