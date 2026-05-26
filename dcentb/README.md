# DcentB - a Data CENTric Backend

**DcentB** is a declarative REST API backend backed by MongoDB.

# Quickstart

Build the project:

```mvn -f ../../pom.xml install -pl public/dcentb -am```

Run the demo backend:

```java -jar target/dcentb-1.0-SNAPSHOT.jar --dcentb.mongodb.db=dcentb-demo```

## OpenAPI
The API is described using OpenAPI specification and the behaviour is declared in an OpenAPI extension.


