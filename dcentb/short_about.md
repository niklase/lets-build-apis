# DcentB - A Declarative data CENTric Backend

Spring boot is an awesome power tool but it is not the API design system that creates a coherent developer experience for REST API consumers. Wherever I look, organizations are having a hard time to build platforms to stream-line API development. Different teams solve the same problems in different ways over and over again.

There are many good technologies (now including AI) but far to often developers have to reinvent the wheel and implement fundamental parts like security, business rules, validation and a decent developer experience for API consumers. Even with good frameworks like Spring boot, code bases become large and hard to maintain.

This is maven project is named "DcentB" and it is part of my the open source github project "Let´s build API:s". 🚀

The API REST style that is built-in by default has been proven to work at different organizations and are originally inspired by Stormpath, Google, Amazon and Stripe. 🧐

👇👇👇👇👇👇👇👇👇👇👇👇

DcentB - A Declarative data CENTric Backend 📡

A decent default declarative backend implementation including:

- Sophisticated CRUD REST API for any type of data entity 🤖
- Security with fine grained access control on data property level per role and/or user 🔐
- Declarative business rules ✅ ❌
- Good API documentation with Swagger UI 📖
- Fully automated test coverage ⚙
- OpenApi-spec-driven 🏛️
- Open source 💯

...and when defaults are not enough:

- No limits to what can be customized
- DcentB is built with Spring boot and can be integrated/extended with any technology in the Spring boot eco-system

Check it out here (quickstart demo only takes a few minutes to setup): [Let´s build API:s (dcentb)](https://letsbuildapis.com)

# OpenAPI-spec-driven CRUD API 

Simple example of a `student` data entity

    GET /students/651243634

    {
      "meta": {
        "href": "/students/651243634"
        "id": "651243634"
        "createdAt": "2026-07-30T12:32:00Z",
        "updatedAt": "2026-07-30T12:32:00Z"
      }
      "firstName": "Peter",
      "lastName": "Andersson"
      "dateOfBirth": "1999-12-13", 
      "email": "peter@example.com"
    }


## Open source software built with Java Spring boot

A decent default CRUD implementation for the repetitive backend coding with the limitless extensibility of Spring boot when (if?) defaults are not enough 

## OpenAPI defines request, response and API data models in JSON Schema 

- No need for Entity classes, Controllers, Services or Repositories, the built-in default CRUD operation implementation is in many (most?) cases enough        
- Standards based update functionality (JSON Merge Patch)
- CRUD operations as POST, PATCH, GET and DELETE

## Quality and control with built-in auto-tests

- Implementation never drifts away from API documentation
- Examples documentation is always in sync with implementation as tests are added to OpenAPI documentation

## True REST API with use of links and HTTP methods

...see demo for examples

## Declarative business rules (JSON Schema based)

...see demo for examples

## Authentication by API keys (Cookies, OIDC, Oauth2 ...) 

Any authentication mechanism is easily added and fully separated from authorization.

## Declarative fine grained attribute based authorization

- Users can never modify or read more data than allowed - not even the existence of data is dislosed when users are not allowed to know.
- Access rules are simple JSON Schemas and access control is auto-tested end-to-end.






