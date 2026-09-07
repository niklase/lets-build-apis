# OpenAPI-driven backend development with full API test coverage and API documentation examples from test execution

A backend can be built from an OpenAPI specification and verified
through component scenarios that also provide concrete examples for the
API documentation. This is the approach of DcentB.

Four things are connected that are otherwise often maintained
separately:

-   the API specification
-   the running backend
-   component verification
-   API documentation examples

## 1. Start with the API specification

OpenAPI defines the HTTP contract. JSON Schema defines the data
structures and validation rules. Declarative configuration adds business
rules and authorization.

![OpenAPI, JSON Schema and rules form the API
definition](./pics/openapi-test-driven-dev/oas-driven-dev-1.png)

The result is a declarative definition of the API that can be used by
the backend at runtime.

## 2. Build the backend from the specification

The declarative model is used by the backend to implement the API
behavior.

The resulting component exposes a real HTTP API connected to a real
database.

![Declarative API definition used by the running API and
database](./pics/openapi-test-driven-dev/oas-driven-dev-2.png)

This reduces the amount of API behavior that has to be implemented
separately in controllers and other application code.

The specification is not only documentation of the backend. It is part
of how the backend works.

## 3. Describe behavior as executable scenarios

Each component test is expressed as a sequence of **GIVEN → WHEN →
THEN** steps.

**GIVEN** establishes a known database state.

``` json
{
  "insert": [ ... ]
}
```

**WHEN** performs an explicit HTTP request.

``` text
POST /resource
GET /resource/1
PATCH /resource/1
DELETE /resource/1
```

**THEN** verifies the HTTP result, including both successful and
unsuccessful responses.

``` text
201 Created
200 OK
204 No Content
400 Bad Request
403 Forbidden
404 Not Found
```

![GIVEN, WHEN and THEN component test
scenario](./pics/openapi-test-driven-dev/oas-driven-dev-3.png)

Successful modifying operations change the database state.

This is important because the steps are not isolated tests. The next
HTTP call operates on the state resulting from the previous successful
operation.

A scenario can therefore describe a complete interaction with the API:

> **As a client I can create a resource, retrieve it, modify it, delete
> it and verify that it no longer exists.**

Other scenarios can describe validation, authorization and business-rule
failures in the same way.

The scenario becomes a readable description of API behavior as well as
an executable test.

## 4. Run the scenario against the actual component

The scenario is executed through the same HTTP interface that an API
consumer uses.

![Component scenario exercising the running API and real
database](./pics/openapi-test-driven-dev/oas-driven-dev-4.png)

The test therefore covers more than a controller method or an isolated
piece of application code.

A scenario can exercise several parts of the component together:

-   HTTP routing and serialization
-   request validation
-   authorization
-   business rules
-   database operations
-   state transitions
-   response serialization
-   error handling

The database is also part of the scenario. GIVEN establishes its initial
state, and successful operations modify that state as the scenario
progresses.

This makes the component test a verification of the API's externally
observable behavior rather than an implementation-specific test.

## 5. Use executed interactions as OpenAPI examples

Every WHEN/THEN pair already contains information that is useful in API
documentation.

A successful interaction contains an actual request and its expected
successful response.

A failure scenario contains the request together with the expected error
response.

![Executed API scenarios become OpenAPI request and response
examples](./pics/openapi-test-driven-dev/oas-driven-dev-5.png)

These interactions can be added to the OpenAPI specification as examples
for:

-   requests
-   successful responses
-   validation errors
-   authorization errors
-   not-found responses
-   other API errors

The examples are therefore not maintained independently from the tests.

They originate from scenarios that are executed against the API.

This is particularly useful for error responses. Instead of documenting
only the schema of an error object, the OpenAPI documentation can show
concrete examples of errors that API consumers may encounter.

The relationship becomes:

**scenario → executed request/response → OpenAPI example**

Testing and documentation are still different concerns, but they use the
same executed interactions as their source.

## 6. Run the same verification locally and in CI/CD

The complete component test suite can be run during development:

``` shell
$ mvn verify
```

![Run the component scenarios locally and in
CI/CD](./pics/openapi-test-driven-dev/oas-driven-dev-6.png)

The same command can be executed by the CI/CD pipeline.

Because the API and database are tested as a component rather than
requiring deployment into a complete external environment, the scenarios
can typically execute in seconds.

This creates a short development loop:

1.  Change the specification, rules or implementation.
2.  Run `mvn verify`.
3.  Execute the API scenarios.
4.  See which scenarios pass or fail.
5.  Make the next change.

The same scenarios later run in CI/CD to detect regressions.

## Connecting specification, verification and documentation

The central idea is not simply to generate tests from OpenAPI or to
generate OpenAPI from tests.

The OpenAPI specification defines the API contract and is used to drive
the backend.

The component scenarios then exercise that backend through real HTTP
calls and verify its behavior against a known database state.

The requests and responses from those scenarios provide concrete
examples for the OpenAPI documentation.

This creates a continuous relationship:

**OpenAPI specification → running API → executable scenarios → verified
behavior → OpenAPI examples**

When the API changes, the scenarios provide immediate feedback about the
behavior, while the executed examples keep the documentation connected
to what is actually being tested.
