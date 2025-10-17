package com.zuunr.api.design;

import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import com.zuunr.json.JsonValueFactory;
import com.zuunr.json.pointer.JsonPointer;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class ApiDesignLinterTest {

    private static final JsonObject INFO_MODEL = JsonValueFactory.create("""
            {
                "$defs": {
                    "persons": {
                        "item": {
                            "type": "object",
                            "properties": {
                                "name": {
                                    "type": "string"
                                },
                                "age": {
                                    "type": "integer"
                                }
                            }
                        },
                        "collection": {
                            "type": "object",
                            "properties": {
                                "items": {
                                    "type": "array",
                                    "items": {
                                        "$ref": "#/$defs/persons/item"
                                    }
                                }
                            }
                        }
                    }
                }
            }
            """).getJsonObject();

    @Test
    void readByGetWithoutQueryOk() {
        JsonObject result1 = ApiDesignLinter.validateApiDesign(INFO_MODEL, JsonObject.EMPTY.put("method", "get").put("uri", "/persons"));
        MatcherAssert.assertThat(result1.get("ok"), Matchers.is(JsonValue.TRUE));
    }

    @Test
    void readNonExistingCollectionByGetError() {
        JsonObject result1 = ApiDesignLinter.validateApiDesign(INFO_MODEL, JsonObject.EMPTY.put("method", "get").put("uri", "/non-existing-things"));
        MatcherAssert.assertThat(result1.get("ok"), Matchers.is(JsonValue.FALSE));
        MatcherAssert.assertThat(result1.get("error").getString(), Matchers.is("There is no collection named 'non-existing-things'"));
    }

    @Test
    void readByGetOk() {
        JsonObject result1 = ApiDesignLinter.validateApiDesign(INFO_MODEL, JsonObject.EMPTY.put("method", "get").put("uri", "/persons?filter.name.eq=Olle&offset=10&limit=100"));
        MatcherAssert.assertThat(result1.get("ok"), Matchers.is(JsonValue.TRUE));
    }

    @Test
    void createByPostError() {
        JsonObject result2 = ApiDesignLinter.validateApiDesign(INFO_MODEL, JsonObject.EMPTY.put("method", "post").put("uri", "/persons").put("headers", JsonObject.EMPTY.put("content-type", JsonArray.of("application/json"))).put("body", JsonObject.EMPTY.put("name2", "Olle").asJson()));
        MatcherAssert.assertThat(result2.get("ok"), Matchers.is(JsonValue.FALSE));
    }

    @Test
    void createByPostOk() {
        JsonObject result3 = ApiDesignLinter.validateApiDesign(INFO_MODEL, JsonObject.EMPTY.put("method", "post").put("uri", "/persons").put("headers", JsonObject.EMPTY.put("content-type", JsonArray.of("application/json"))).put("body", JsonObject.EMPTY.put("name", "Olle").asJson()));
        MatcherAssert.assertThat(result3.get("ok"), Matchers.is(JsonValue.TRUE));
    }

    @Test
    void readByPostOk() {
        JsonObject result4 = ApiDesignLinter.validateApiDesign(INFO_MODEL, JsonObject.EMPTY.put("method", "post").put("uri", "/persons/getCollection").put("headers", JsonObject.EMPTY.put("content-type", JsonArray.of("application/json"))).put("body", JsonObject.EMPTY.put("filter", JsonObject.EMPTY.put("name", JsonObject.EMPTY.put("eq", "olle"))).asJson()));
        MatcherAssert.assertThat(result4.get("ok"), Matchers.is(JsonValue.TRUE));
    }

    @Test
    void readByPostError() {
        JsonObject result4 = ApiDesignLinter.validateApiDesign(INFO_MODEL, JsonObject.EMPTY.put("method", "post").put("uri", "/persons/getCollection").put("headers", JsonObject.EMPTY.put("content-type", JsonArray.of("application/json"))).put("body", JsonObject.EMPTY.put("filter", JsonObject.EMPTY.put("name2", JsonObject.EMPTY.put("eq", "olle"))).asJson()));
        MatcherAssert.assertThat(result4.get("ok"), Matchers.is(JsonValue.FALSE));
    }

    @Test
    void updateByPatchOk() {
        JsonObject result4 = ApiDesignLinter.validateApiDesign(INFO_MODEL, JsonObject.EMPTY.put("method", "patch").put("uri", "/persons/123").put("headers", JsonObject.EMPTY.put("content-type", JsonArray.of("application/json"))).put("body", JsonObject.EMPTY.put("name", "Peter").asJson()));
        MatcherAssert.assertThat(result4.get("ok"), Matchers.is(JsonValue.TRUE));
    }

    @Test
    void updateByPatchError() {
        JsonObject result4 = ApiDesignLinter.validateApiDesign(INFO_MODEL, JsonObject.EMPTY.put("method", "patch").put("uri", "/persons/123").put("headers", JsonObject.EMPTY.put("content-type", JsonArray.of("application/json"))).put("body", JsonObject.EMPTY.put("age", 22).asJson()));
        MatcherAssert.assertThat(result4.get("ok"), Matchers.is(JsonValue.TRUE));
    }



    private static JsonObject LARGER_INFO_MODEL = JsonValueFactory.create("""
                {
                  "$ref": "#/$defs/orders/item",
                  "$defs": {
                    "meta": {
                      "item": {
                        "properties": {
                          "href": {
                            "type": "string"
                          },
                          "id": {
                            "type": "string"
                          }
                        }
                      },
                      "collection": {
                        "properties": {
                          "href": {
                            "type": "string"
                          },
                          "next": {
                            "properties": {
                              "meta": {
                                "$ref": "#/$defs/meta/collection"
                              }
                            }
                          },
                          "prev": {
                            "properties": {
                              "meta": {
                                "$ref": "#/$defs/meta/collection"
                              }
                            }
                          }
                        }
                      }
                    },
                    "orders": {
                      "item": {
                        "properties": {
                          "meta": {
                            "$ref": "#/$defs/meta/item"
                          },
                          "productName": {
                            "type": "string"
                          },
                          "quantity": {
                            "type": "integer"
                          },
                          "customer": {
                            "$ref": "#/$defs/customers/item"
                          }
                        },
                        "type": "object",
                        "additionalProperties": false
                      },
                      "collection": {
                        "type": "object",
                        "properties": {
                          "items": {
                            "type": "array",
                            "items": {
                              "$ref": "#/$defs/orders/item"
                            }
                          }
                        }
                      }
                    },
                    "customers": {
                      "item": {
                        "properties": {
                          "meta": {
                            "$ref": "#/$defs/meta/item"
                          },
                          "name": {
                            "type": "string"
                          },
                          "vatNumber": {
                            "type": "string"
                          }
                        },
                        "type": "object",
                        "additionalProperties": false
                      },
                      "collection": {
                        "type": "object",
                        "properties": {
                          "items": {
                            "type": "array",
                            "items": {
                              "$ref": "#/$defs/customers/item"
                            }
                          }
                        }
                      }
                    }
                  }
                }
                """).getJsonObject();
    @Test
    void largerTest() {
        JsonObject request = JsonObject.EMPTY.put("method", "post").put("uri", "/orders/getCollection").put("headers", JsonObject.EMPTY.put("content-type", JsonArray.of("application/json"))).put("body", JsonObject.EMPTY.put("filter", JsonObject.EMPTY.put("customer.vatNumber", JsonObject.EMPTY.put("eq", "abc123"))).asJson());
        JsonObject result = ApiDesignLinter.validateApiDesign(LARGER_INFO_MODEL, request);
        MatcherAssert.assertThat(result.asJson(), result.get(JsonPointer.of("/deserializationResult/ok")).getBoolean(), Matchers.is(true));
    }

    @Test
    void createOpenApiDocument(){

        JsonObject initialOpenApiDoc = JsonValueFactory.create("""
                {
                  "openapi": "3.1.0",
                  "info": {
                    "title": "Generated OAS 3.1 document",
                    "description": "This is an OpenAPI document specifying possible API requests according to the API guidelines. It is based on the the information model provided as a JSON schema",
                    "termsOfService": "https://example.com/terms/",
                    "contact": {
                      "email": "apiteam@example.com"
                    },
                    "license": {
                      "name": "License XYZ...",
                      "url": "https://example.com/licenses/LICENSE.html"
                    },
                    "version": "1.0.0"
                  },
                  "externalDocs": {
                    "description": "Find out more about Swagger",
                    "url": "http://swagger.io"
                  },
                  "servers": [
                    {
                      "url": "https://api.example.com"
                    }
                  ]
                }  
                """).getJsonObject();

        JsonObject result = ApiDesignLinter.createOpenApiDocument(initialOpenApiDoc, JsonArray.of("customers", "orders"), LARGER_INFO_MODEL);

        JsonObject expectedResult = JsonValueFactory.create(expectedOpenApiSpec()).getJsonObject();

        MatcherAssert.assertThat(result, Matchers.is(expectedResult));
    }

    String expectedOpenApiSpec(){
        return """
                {
                  "servers": [
                    {
                      "url": "https://api.example.com"
                    }
                  ],
                  "info": {
                    "license": {
                      "url": "https://example.com/licenses/LICENSE.html",
                      "name": "License XYZ..."
                    },
                    "title": "Generated OAS 3.1 document",
                    "termsOfService": "https://example.com/terms/",
                    "version": "1.0.0",
                    "contact": {
                      "email": "apiteam@example.com"
                    },
                    "description": "This is an OpenAPI document specifying possible API requests according to the API guidelines. It is based on the the information model provided as a JSON schema"
                  },
                  "tags": [
                    {
                      "name": "customers",
                      "description": "..."
                    },
                    {
                      "name": "orders",
                      "description": "..."
                    }
                  ],
                  "externalDocs": {
                    "url": "http://swagger.io",
                    "description": "Find out more about Swagger"
                  },
                  "paths": {
                    "/orders": {
                      "post": {
                        "responses": {
                          "201": {
                            "content": {
                              "application/json": {
                                "schema": {
                                  "properties": {
                                    "quantity": {
                                      "type": "integer"
                                    },
                                    "productName": {
                                      "type": "string"
                                    },
                                    "meta": {
                                      "properties": {
                                        "id": {
                                          "type": "string"
                                        },
                                        "href": {
                                          "type": "string"
                                        }
                                      },
                                      "additionalProperties": false
                                    },
                                    "customer": {
                                      "properties": {
                                        "name": {
                                          "type": "string"
                                        },
                                        "meta": {
                                          "properties": {
                                            "id": {
                                              "type": "string"
                                            },
                                            "href": {
                                              "type": "string"
                                            }
                                          },
                                          "additionalProperties": false
                                        },
                                        "vatNumber": {
                                          "type": "string"
                                        }
                                      },
                                      "type": "object",
                                      "additionalProperties": false
                                    }
                                  },
                                  "type": "object",
                                  "additionalProperties": false
                                }
                              }
                            },
                            "description": ""
                          }
                        },
                        "tags": [
                          "orders"
                        ],
                        "requestBody": {
                          "content": {
                            "application/json": {
                              "schema": {
                                "properties": {
                                  "quantity": {
                                    "type": "integer"
                                  },
                                  "productName": {
                                    "type": "string"
                                  },
                                  "meta": {
                                    "properties": {
                                      "id": {
                                        "type": "string"
                                      },
                                      "href": {
                                        "type": "string"
                                      }
                                    },
                                    "additionalProperties": false
                                  },
                                  "customer": {
                                    "properties": {
                                      "name": {
                                        "type": "string"
                                      },
                                      "meta": {
                                        "properties": {
                                          "id": {
                                            "type": "string"
                                          },
                                          "href": {
                                            "type": "string"
                                          }
                                        },
                                        "additionalProperties": false
                                      },
                                      "vatNumber": {
                                        "type": "string"
                                      }
                                    },
                                    "type": "object",
                                    "additionalProperties": false
                                  }
                                },
                                "type": "object",
                                "additionalProperties": false
                              }
                            }
                          },
                          "required": true
                        }
                      },
                      "get": {
                        "responses": {
                          "200": {
                            "content": {
                              "application/json": {
                                "schema": {
                                  "properties": {
                                    "items": {
                                      "items": {
                                        "properties": {
                                          "quantity": {
                                            "type": "integer"
                                          },
                                          "productName": {
                                            "type": "string"
                                          },
                                          "meta": {
                                            "properties": {
                                              "id": {
                                                "type": "string"
                                              },
                                              "href": {
                                                "type": "string"
                                              }
                                            },
                                            "additionalProperties": false
                                          },
                                          "customer": {
                                            "properties": {
                                              "name": {
                                                "type": "string"
                                              },
                                              "meta": {
                                                "properties": {
                                                  "id": {
                                                    "type": "string"
                                                  },
                                                  "href": {
                                                    "type": "string"
                                                  }
                                                },
                                                "additionalProperties": false
                                              },
                                              "vatNumber": {
                                                "type": "string"
                                              }
                                            },
                                            "type": "object",
                                            "additionalProperties": false
                                          }
                                        },
                                        "type": "object",
                                        "additionalProperties": false
                                      },
                                      "type": "array"
                                    }
                                  },
                                  "type": "object",
                                  "additionalProperties": false
                                }
                              }
                            }
                          }
                        },
                        "tags": [
                          "orders"
                        ],
                        "parameters": [
                          {
                            "schema": {
                              "type": "integer"
                            },
                            "name": "filter.quantity.eq",
                            "required": false,
                            "in": "query"
                          },
                          {
                            "schema": {
                              "type": "integer"
                            },
                            "name": "filter.quantity.gt",
                            "required": false,
                            "in": "query"
                          },
                          {
                            "schema": {
                              "type": "integer"
                            },
                            "name": "filter.quantity.gte",
                            "required": false,
                            "in": "query"
                          },
                          {
                            "schema": {
                              "type": "integer"
                            },
                            "name": "filter.quantity.lt",
                            "required": false,
                            "in": "query"
                          },
                          {
                            "schema": {
                              "type": "integer"
                            },
                            "name": "filter.quantity.lte",
                            "required": false,
                            "in": "query"
                          },
                          {
                            "schema": {
                              "type": "integer"
                            },
                            "name": "filter.quantity.ne",
                            "required": false,
                            "in": "query"
                          },
                          {
                            "schema": {
                              "type": "string"
                            },
                            "name": "filter.productName.eq",
                            "required": false,
                            "in": "query"
                          },
                          {
                            "schema": {
                              "type": "string"
                            },
                            "name": "filter.productName.gt",
                            "required": false,
                            "in": "query"
                          },
                          {
                            "schema": {
                              "type": "string"
                            },
                            "name": "filter.productName.gte",
                            "required": false,
                            "in": "query"
                          },
                          {
                            "schema": {
                              "type": "string"
                            },
                            "name": "filter.productName.lt",
                            "required": false,
                            "in": "query"
                          },
                          {
                            "schema": {
                              "type": "string"
                            },
                            "name": "filter.productName.lte",
                            "required": false,
                            "in": "query"
                          },
                          {
                            "schema": {
                              "type": "string"
                            },
                            "name": "filter.productName.ne",
                            "required": false,
                            "in": "query"
                          },
                          {
                            "schema": {
                              "type": "string"
                            },
                            "name": "filter.productName.regex",
                            "required": false,
                            "in": "query"
                          },
                          {
                            "schema": {
                              "type": "string"
                            },
                            "name": "filter.meta.id.eq",
                            "required": false,
                            "in": "query"
                          },
                          {
                            "schema": {
                              "type": "string"
                            },
                            "name": "filter.meta.id.gt",
                            "required": false,
                            "in": "query"
                          },
                          {
                            "schema": {
                              "type": "string"
                            },
                            "name": "filter.meta.id.gte",
                            "required": false,
                            "in": "query"
                          },
                          {
                            "schema": {
                              "type": "string"
                            },
                            "name": "filter.meta.id.lt",
                            "required": false,
                            "in": "query"
                          },
                          {
                            "schema": {
                              "type": "string"
                            },
                            "name": "filter.meta.id.lte",
                            "required": false,
                            "in": "query"
                          },
                          {
                            "schema": {
                              "type": "string"
                            },
                            "name": "filter.meta.id.ne",
                            "required": false,
                            "in": "query"
                          },
                          {
                            "schema": {
                              "type": "string"
                            },
                            "name": "filter.meta.id.regex",
                            "required": false,
                            "in": "query"
                          },
                          {
                            "schema": {
                              "type": "string"
                            },
                            "name": "filter.meta.href.eq",
                            "required": false,
                            "in": "query"
                          },
                          {
                            "schema": {
                              "type": "string"
                            },
                            "name": "filter.meta.href.gt",
                            "required": false,
                            "in": "query"
                          },
                          {
                            "schema": {
                              "type": "string"
                            },
                            "name": "filter.meta.href.gte",
                            "required": false,
                            "in": "query"
                          },
                          {
                            "schema": {
                              "type": "string"
                            },
                            "name": "filter.meta.href.lt",
                            "required": false,
                            "in": "query"
                          },
                          {
                            "schema": {
                              "type": "string"
                            },
                            "name": "filter.meta.href.lte",
                            "required": false,
                            "in": "query"
                          },
                          {
                            "schema": {
                              "type": "string"
                            },
                            "name": "filter.meta.href.ne",
                            "required": false,
                            "in": "query"
                          },
                          {
                            "schema": {
                              "type": "string"
                            },
                            "name": "filter.meta.href.regex",
                            "required": false,
                            "in": "query"
                          },
                          {
                            "schema": {
                              "type": "string"
                            },
                            "name": "filter.customer.name.eq",
                            "required": false,
                            "in": "query"
                          },
                          {
                            "schema": {
                              "type": "string"
                            },
                            "name": "filter.customer.name.gt",
                            "required": false,
                            "in": "query"
                          },
                          {
                            "schema": {
                              "type": "string"
                            },
                            "name": "filter.customer.name.gte",
                            "required": false,
                            "in": "query"
                          },
                          {
                            "schema": {
                              "type": "string"
                            },
                            "name": "filter.customer.name.lt",
                            "required": false,
                            "in": "query"
                          },
                          {
                            "schema": {
                              "type": "string"
                            },
                            "name": "filter.customer.name.lte",
                            "required": false,
                            "in": "query"
                          },
                          {
                            "schema": {
                              "type": "string"
                            },
                            "name": "filter.customer.name.ne",
                            "required": false,
                            "in": "query"
                          },
                          {
                            "schema": {
                              "type": "string"
                            },
                            "name": "filter.customer.name.regex",
                            "required": false,
                            "in": "query"
                          },
                          {
                            "schema": {
                              "type": "string"
                            },
                            "name": "filter.customer.meta.id.eq",
                            "required": false,
                            "in": "query"
                          },
                          {
                            "schema": {
                              "type": "string"
                            },
                            "name": "filter.customer.meta.id.gt",
                            "required": false,
                            "in": "query"
                          },
                          {
                            "schema": {
                              "type": "string"
                            },
                            "name": "filter.customer.meta.id.gte",
                            "required": false,
                            "in": "query"
                          },
                          {
                            "schema": {
                              "type": "string"
                            },
                            "name": "filter.customer.meta.id.lt",
                            "required": false,
                            "in": "query"
                          },
                          {
                            "schema": {
                              "type": "string"
                            },
                            "name": "filter.customer.meta.id.lte",
                            "required": false,
                            "in": "query"
                          },
                          {
                            "schema": {
                              "type": "string"
                            },
                            "name": "filter.customer.meta.id.ne",
                            "required": false,
                            "in": "query"
                          },
                          {
                            "schema": {
                              "type": "string"
                            },
                            "name": "filter.customer.meta.id.regex",
                            "required": false,
                            "in": "query"
                          },
                          {
                            "schema": {
                              "type": "string"
                            },
                            "name": "filter.customer.meta.href.eq",
                            "required": false,
                            "in": "query"
                          },
                          {
                            "schema": {
                              "type": "string"
                            },
                            "name": "filter.customer.meta.href.gt",
                            "required": false,
                            "in": "query"
                          },
                          {
                            "schema": {
                              "type": "string"
                            },
                            "name": "filter.customer.meta.href.gte",
                            "required": false,
                            "in": "query"
                          },
                          {
                            "schema": {
                              "type": "string"
                            },
                            "name": "filter.customer.meta.href.lt",
                            "required": false,
                            "in": "query"
                          },
                          {
                            "schema": {
                              "type": "string"
                            },
                            "name": "filter.customer.meta.href.lte",
                            "required": false,
                            "in": "query"
                          },
                          {
                            "schema": {
                              "type": "string"
                            },
                            "name": "filter.customer.meta.href.ne",
                            "required": false,
                            "in": "query"
                          },
                          {
                            "schema": {
                              "type": "string"
                            },
                            "name": "filter.customer.meta.href.regex",
                            "required": false,
                            "in": "query"
                          },
                          {
                            "schema": {
                              "type": "string"
                            },
                            "name": "filter.customer.vatNumber.eq",
                            "required": false,
                            "in": "query"
                          },
                          {
                            "schema": {
                              "type": "string"
                            },
                            "name": "filter.customer.vatNumber.gt",
                            "required": false,
                            "in": "query"
                          },
                          {
                            "schema": {
                              "type": "string"
                            },
                            "name": "filter.customer.vatNumber.gte",
                            "required": false,
                            "in": "query"
                          },
                          {
                            "schema": {
                              "type": "string"
                            },
                            "name": "filter.customer.vatNumber.lt",
                            "required": false,
                            "in": "query"
                          },
                          {
                            "schema": {
                              "type": "string"
                            },
                            "name": "filter.customer.vatNumber.lte",
                            "required": false,
                            "in": "query"
                          },
                          {
                            "schema": {
                              "type": "string"
                            },
                            "name": "filter.customer.vatNumber.ne",
                            "required": false,
                            "in": "query"
                          },
                          {
                            "schema": {
                              "type": "string"
                            },
                            "name": "filter.customer.vatNumber.regex",
                            "required": false,
                            "in": "query"
                          },
                          {
                            "schema": {
                              "type": "integer"
                            },
                            "name": "offset",
                            "required": false,
                            "in": "query"
                          },
                          {
                            "schema": {
                              "type": "integer"
                            },
                            "name": "limit",
                            "required": false,
                            "in": "query"
                          },
                          {
                            "schema": {
                              "items": {
                                "pattern": "^(quantity( asc| desc)?|productName( asc| desc)?|meta[.]id( asc| desc)?|meta[.]href( asc| desc)?|customer[.]name( asc| desc)?|customer[.]meta[.]id( asc| desc)?|customer[.]meta[.]href( asc| desc)?|customer[.]vatNumber( asc| desc)?)$",
                                "type": "string"
                              },
                              "type": "array"
                            },
                            "name": "orderBy",
                            "explode": false,
                            "required": false,
                            "in": "query"
                          }
                        ]
                      }
                    },
                    "/customers/{id}": {
                      "delete": {
                        "responses": {
                          "204": {
                            "description": "No content"
                          }
                        },
                        "tags": [
                          "customers"
                        ],
                        "parameters": [
                          {
                            "schema": {
                              "type": "string"
                            },
                            "name": "id",
                            "required": true,
                            "in": "path"
                          }
                        ]
                      },
                      "patch": {
                        "responses": {
                          "200": {
                            "content": {
                              "application/json": {
                                "schema": {
                                  "properties": {
                                    "name": {
                                      "type": "string"
                                    },
                                    "meta": {
                                      "properties": {
                                        "id": {
                                          "type": "string"
                                        },
                                        "href": {
                                          "type": "string"
                                        }
                                      },
                                      "additionalProperties": false
                                    },
                                    "vatNumber": {
                                      "type": "string"
                                    }
                                  },
                                  "type": "object",
                                  "additionalProperties": false
                                }
                              }
                            },
                            "description": "OK"
                          }
                        },
                        "tags": [
                          "customers"
                        ],
                        "requestBody": {
                          "content": {
                            "application/json": {
                              "schema": {
                                "properties": {
                                  "name": {
                                    "type": "string"
                                  },
                                  "meta": {
                                    "properties": {
                                      "id": {
                                        "type": "string"
                                      },
                                      "href": {
                                        "type": "string"
                                      }
                                    },
                                    "additionalProperties": false
                                  },
                                  "vatNumber": {
                                    "type": "string"
                                  }
                                },
                                "type": "object",
                                "additionalProperties": false
                              }
                            }
                          },
                          "required": true
                        },
                        "parameters": [
                          {
                            "schema": {
                              "type": "string"
                            },
                            "name": "id",
                            "required": true,
                            "in": "path"
                          }
                        ]
                      },
                      "get": {
                        "responses": {
                          "200": {
                            "content": {
                              "application/json": {
                                "schema": {
                                  "properties": {
                                    "name": {
                                      "type": "string"
                                    },
                                    "meta": {
                                      "properties": {
                                        "id": {
                                          "type": "string"
                                        },
                                        "href": {
                                          "type": "string"
                                        }
                                      },
                                      "additionalProperties": false
                                    },
                                    "vatNumber": {
                                      "type": "string"
                                    }
                                  },
                                  "type": "object",
                                  "additionalProperties": false
                                }
                              }
                            },
                            "description": "OK"
                          }
                        },
                        "tags": [
                          "customers"
                        ],
                        "parameters": [
                          {
                            "schema": {
                              "type": "string"
                            },
                            "name": "id",
                            "required": true,
                            "in": "path"
                          }
                        ]
                      }
                    },
                    "/orders/{id}": {
                      "delete": {
                        "responses": {
                          "204": {
                            "description": "No content"
                          }
                        },
                        "tags": [
                          "orders"
                        ],
                        "parameters": [
                          {
                            "schema": {
                              "type": "string"
                            },
                            "name": "id",
                            "required": true,
                            "in": "path"
                          }
                        ]
                      },
                      "patch": {
                        "responses": {
                          "200": {
                            "content": {
                              "application/json": {
                                "schema": {
                                  "properties": {
                                    "quantity": {
                                      "type": "integer"
                                    },
                                    "productName": {
                                      "type": "string"
                                    },
                                    "meta": {
                                      "properties": {
                                        "id": {
                                          "type": "string"
                                        },
                                        "href": {
                                          "type": "string"
                                        }
                                      },
                                      "additionalProperties": false
                                    },
                                    "customer": {
                                      "properties": {
                                        "name": {
                                          "type": "string"
                                        },
                                        "meta": {
                                          "properties": {
                                            "id": {
                                              "type": "string"
                                            },
                                            "href": {
                                              "type": "string"
                                            }
                                          },
                                          "additionalProperties": false
                                        },
                                        "vatNumber": {
                                          "type": "string"
                                        }
                                      },
                                      "type": "object",
                                      "additionalProperties": false
                                    }
                                  },
                                  "type": "object",
                                  "additionalProperties": false
                                }
                              }
                            },
                            "description": "OK"
                          }
                        },
                        "tags": [
                          "orders"
                        ],
                        "requestBody": {
                          "content": {
                            "application/json": {
                              "schema": {
                                "properties": {
                                  "quantity": {
                                    "type": "integer"
                                  },
                                  "productName": {
                                    "type": "string"
                                  },
                                  "meta": {
                                    "properties": {
                                      "id": {
                                        "type": "string"
                                      },
                                      "href": {
                                        "type": "string"
                                      }
                                    },
                                    "additionalProperties": false
                                  },
                                  "customer": {
                                    "properties": {
                                      "name": {
                                        "type": "string"
                                      },
                                      "meta": {
                                        "properties": {
                                          "id": {
                                            "type": "string"
                                          },
                                          "href": {
                                            "type": "string"
                                          }
                                        },
                                        "additionalProperties": false
                                      },
                                      "vatNumber": {
                                        "type": "string"
                                      }
                                    },
                                    "type": "object",
                                    "additionalProperties": false
                                  }
                                },
                                "type": "object",
                                "additionalProperties": false
                              }
                            }
                          },
                          "required": true
                        },
                        "parameters": [
                          {
                            "schema": {
                              "type": "string"
                            },
                            "name": "id",
                            "required": true,
                            "in": "path"
                          }
                        ]
                      },
                      "get": {
                        "responses": {
                          "200": {
                            "content": {
                              "application/json": {
                                "schema": {
                                  "properties": {
                                    "quantity": {
                                      "type": "integer"
                                    },
                                    "productName": {
                                      "type": "string"
                                    },
                                    "meta": {
                                      "properties": {
                                        "id": {
                                          "type": "string"
                                        },
                                        "href": {
                                          "type": "string"
                                        }
                                      },
                                      "additionalProperties": false
                                    },
                                    "customer": {
                                      "properties": {
                                        "name": {
                                          "type": "string"
                                        },
                                        "meta": {
                                          "properties": {
                                            "id": {
                                              "type": "string"
                                            },
                                            "href": {
                                              "type": "string"
                                            }
                                          },
                                          "additionalProperties": false
                                        },
                                        "vatNumber": {
                                          "type": "string"
                                        }
                                      },
                                      "type": "object",
                                      "additionalProperties": false
                                    }
                                  },
                                  "type": "object",
                                  "additionalProperties": false
                                }
                              }
                            },
                            "description": "OK"
                          }
                        },
                        "tags": [
                          "orders"
                        ],
                        "parameters": [
                          {
                            "schema": {
                              "type": "string"
                            },
                            "name": "id",
                            "required": true,
                            "in": "path"
                          }
                        ]
                      }
                    },
                    "/customers": {
                      "post": {
                        "responses": {
                          "201": {
                            "content": {
                              "application/json": {
                                "schema": {
                                  "properties": {
                                    "name": {
                                      "type": "string"
                                    },
                                    "meta": {
                                      "properties": {
                                        "id": {
                                          "type": "string"
                                        },
                                        "href": {
                                          "type": "string"
                                        }
                                      },
                                      "additionalProperties": false
                                    },
                                    "vatNumber": {
                                      "type": "string"
                                    }
                                  },
                                  "type": "object",
                                  "additionalProperties": false
                                }
                              }
                            },
                            "description": ""
                          }
                        },
                        "tags": [
                          "customers"
                        ],
                        "requestBody": {
                          "content": {
                            "application/json": {
                              "schema": {
                                "properties": {
                                  "name": {
                                    "type": "string"
                                  },
                                  "meta": {
                                    "properties": {
                                      "id": {
                                        "type": "string"
                                      },
                                      "href": {
                                        "type": "string"
                                      }
                                    },
                                    "additionalProperties": false
                                  },
                                  "vatNumber": {
                                    "type": "string"
                                  }
                                },
                                "type": "object",
                                "additionalProperties": false
                              }
                            }
                          },
                          "required": true
                        }
                      },
                      "get": {
                        "responses": {
                          "200": {
                            "content": {
                              "application/json": {
                                "schema": {
                                  "properties": {
                                    "items": {
                                      "items": {
                                        "properties": {
                                          "name": {
                                            "type": "string"
                                          },
                                          "meta": {
                                            "properties": {
                                              "id": {
                                                "type": "string"
                                              },
                                              "href": {
                                                "type": "string"
                                              }
                                            },
                                            "additionalProperties": false
                                          },
                                          "vatNumber": {
                                            "type": "string"
                                          }
                                        },
                                        "type": "object",
                                        "additionalProperties": false
                                      },
                                      "type": "array"
                                    }
                                  },
                                  "type": "object",
                                  "additionalProperties": false
                                }
                              }
                            }
                          }
                        },
                        "tags": [
                          "customers"
                        ],
                        "parameters": [
                          {
                            "schema": {
                              "type": "string"
                            },
                            "name": "filter.name.eq",
                            "required": false,
                            "in": "query"
                          },
                          {
                            "schema": {
                              "type": "string"
                            },
                            "name": "filter.name.gt",
                            "required": false,
                            "in": "query"
                          },
                          {
                            "schema": {
                              "type": "string"
                            },
                            "name": "filter.name.gte",
                            "required": false,
                            "in": "query"
                          },
                          {
                            "schema": {
                              "type": "string"
                            },
                            "name": "filter.name.lt",
                            "required": false,
                            "in": "query"
                          },
                          {
                            "schema": {
                              "type": "string"
                            },
                            "name": "filter.name.lte",
                            "required": false,
                            "in": "query"
                          },
                          {
                            "schema": {
                              "type": "string"
                            },
                            "name": "filter.name.ne",
                            "required": false,
                            "in": "query"
                          },
                          {
                            "schema": {
                              "type": "string"
                            },
                            "name": "filter.name.regex",
                            "required": false,
                            "in": "query"
                          },
                          {
                            "schema": {
                              "type": "string"
                            },
                            "name": "filter.meta.id.eq",
                            "required": false,
                            "in": "query"
                          },
                          {
                            "schema": {
                              "type": "string"
                            },
                            "name": "filter.meta.id.gt",
                            "required": false,
                            "in": "query"
                          },
                          {
                            "schema": {
                              "type": "string"
                            },
                            "name": "filter.meta.id.gte",
                            "required": false,
                            "in": "query"
                          },
                          {
                            "schema": {
                              "type": "string"
                            },
                            "name": "filter.meta.id.lt",
                            "required": false,
                            "in": "query"
                          },
                          {
                            "schema": {
                              "type": "string"
                            },
                            "name": "filter.meta.id.lte",
                            "required": false,
                            "in": "query"
                          },
                          {
                            "schema": {
                              "type": "string"
                            },
                            "name": "filter.meta.id.ne",
                            "required": false,
                            "in": "query"
                          },
                          {
                            "schema": {
                              "type": "string"
                            },
                            "name": "filter.meta.id.regex",
                            "required": false,
                            "in": "query"
                          },
                          {
                            "schema": {
                              "type": "string"
                            },
                            "name": "filter.meta.href.eq",
                            "required": false,
                            "in": "query"
                          },
                          {
                            "schema": {
                              "type": "string"
                            },
                            "name": "filter.meta.href.gt",
                            "required": false,
                            "in": "query"
                          },
                          {
                            "schema": {
                              "type": "string"
                            },
                            "name": "filter.meta.href.gte",
                            "required": false,
                            "in": "query"
                          },
                          {
                            "schema": {
                              "type": "string"
                            },
                            "name": "filter.meta.href.lt",
                            "required": false,
                            "in": "query"
                          },
                          {
                            "schema": {
                              "type": "string"
                            },
                            "name": "filter.meta.href.lte",
                            "required": false,
                            "in": "query"
                          },
                          {
                            "schema": {
                              "type": "string"
                            },
                            "name": "filter.meta.href.ne",
                            "required": false,
                            "in": "query"
                          },
                          {
                            "schema": {
                              "type": "string"
                            },
                            "name": "filter.meta.href.regex",
                            "required": false,
                            "in": "query"
                          },
                          {
                            "schema": {
                              "type": "string"
                            },
                            "name": "filter.vatNumber.eq",
                            "required": false,
                            "in": "query"
                          },
                          {
                            "schema": {
                              "type": "string"
                            },
                            "name": "filter.vatNumber.gt",
                            "required": false,
                            "in": "query"
                          },
                          {
                            "schema": {
                              "type": "string"
                            },
                            "name": "filter.vatNumber.gte",
                            "required": false,
                            "in": "query"
                          },
                          {
                            "schema": {
                              "type": "string"
                            },
                            "name": "filter.vatNumber.lt",
                            "required": false,
                            "in": "query"
                          },
                          {
                            "schema": {
                              "type": "string"
                            },
                            "name": "filter.vatNumber.lte",
                            "required": false,
                            "in": "query"
                          },
                          {
                            "schema": {
                              "type": "string"
                            },
                            "name": "filter.vatNumber.ne",
                            "required": false,
                            "in": "query"
                          },
                          {
                            "schema": {
                              "type": "string"
                            },
                            "name": "filter.vatNumber.regex",
                            "required": false,
                            "in": "query"
                          },
                          {
                            "schema": {
                              "type": "integer"
                            },
                            "name": "offset",
                            "required": false,
                            "in": "query"
                          },
                          {
                            "schema": {
                              "type": "integer"
                            },
                            "name": "limit",
                            "required": false,
                            "in": "query"
                          },
                          {
                            "schema": {
                              "items": {
                                "pattern": "^(name( asc| desc)?|meta[.]id( asc| desc)?|meta[.]href( asc| desc)?|vatNumber( asc| desc)?)$",
                                "type": "string"
                              },
                              "type": "array"
                            },
                            "name": "orderBy",
                            "explode": false,
                            "required": false,
                            "in": "query"
                          }
                        ]
                      }
                    },
                    "/orders/getCollection": {
                      "post": {
                        "responses": {
                          "200": {
                            "content": {
                              "application/json": {
                                "schema": {
                                  "properties": {
                                    "items": {
                                      "items": {
                                        "properties": {
                                          "quantity": {
                                            "type": "integer"
                                          },
                                          "productName": {
                                            "type": "string"
                                          },
                                          "meta": {
                                            "properties": {
                                              "id": {
                                                "type": "string"
                                              },
                                              "href": {
                                                "type": "string"
                                              }
                                            },
                                            "additionalProperties": false
                                          },
                                          "customer": {
                                            "properties": {
                                              "name": {
                                                "type": "string"
                                              },
                                              "meta": {
                                                "properties": {
                                                  "id": {
                                                    "type": "string"
                                                  },
                                                  "href": {
                                                    "type": "string"
                                                  }
                                                },
                                                "additionalProperties": false
                                              },
                                              "vatNumber": {
                                                "type": "string"
                                              }
                                            },
                                            "type": "object",
                                            "additionalProperties": false
                                          }
                                        },
                                        "type": "object",
                                        "additionalProperties": false
                                      },
                                      "type": "array"
                                    }
                                  },
                                  "type": "object",
                                  "additionalProperties": false
                                }
                              }
                            },
                            "description": "OK"
                          }
                        },
                        "tags": [
                          "orders"
                        ],
                        "requestBody": {
                          "content": {
                            "application/json": {
                              "schema": {
                                "properties": {
                                  "offset": {
                                    "type": "integer"
                                  },
                                  "limit": {
                                    "type": "integer"
                                  },
                                  "orderBy": {
                                    "items": {
                                      "pattern": "^(quantity( asc| desc)?|productName( asc| desc)?|meta[.]id( asc| desc)?|meta[.]href( asc| desc)?|customer[.]name( asc| desc)?|customer[.]meta[.]id( asc| desc)?|customer[.]meta[.]href( asc| desc)?|customer[.]vatNumber( asc| desc)?)$",
                                      "type": "string"
                                    },
                                    "type": "array"
                                  },
                                  "filter": {
                                    "properties": {
                                      "meta.id": {
                                        "properties": {
                                          "lt": {
                                            "type": "string"
                                          },
                                          "ne": {
                                            "type": "string"
                                          },
                                          "eq": {
                                            "type": "string"
                                          },
                                          "regex": {
                                            "type": "string"
                                          },
                                          "gt": {
                                            "type": "string"
                                          },
                                          "gte": {
                                            "type": "string"
                                          },
                                          "lte": {
                                            "type": "string"
                                          }
                                        },
                                        "additionalProperties": false
                                      },
                                      "customer.name": {
                                        "properties": {
                                          "lt": {
                                            "type": "string"
                                          },
                                          "ne": {
                                            "type": "string"
                                          },
                                          "eq": {
                                            "type": "string"
                                          },
                                          "regex": {
                                            "type": "string"
                                          },
                                          "gt": {
                                            "type": "string"
                                          },
                                          "gte": {
                                            "type": "string"
                                          },
                                          "lte": {
                                            "type": "string"
                                          }
                                        },
                                        "additionalProperties": false
                                      },
                                      "customer.meta.href": {
                                        "properties": {
                                          "lt": {
                                            "type": "string"
                                          },
                                          "ne": {
                                            "type": "string"
                                          },
                                          "eq": {
                                            "type": "string"
                                          },
                                          "regex": {
                                            "type": "string"
                                          },
                                          "gt": {
                                            "type": "string"
                                          },
                                          "gte": {
                                            "type": "string"
                                          },
                                          "lte": {
                                            "type": "string"
                                          }
                                        },
                                        "additionalProperties": false
                                      },
                                      "quantity": {
                                        "properties": {
                                          "lt": {
                                            "type": "integer"
                                          },
                                          "ne": {
                                            "type": "integer"
                                          },
                                          "eq": {
                                            "type": "integer"
                                          },
                                          "gt": {
                                            "type": "integer"
                                          },
                                          "gte": {
                                            "type": "integer"
                                          },
                                          "lte": {
                                            "type": "integer"
                                          }
                                        },
                                        "additionalProperties": false
                                      },
                                      "productName": {
                                        "properties": {
                                          "lt": {
                                            "type": "string"
                                          },
                                          "ne": {
                                            "type": "string"
                                          },
                                          "eq": {
                                            "type": "string"
                                          },
                                          "regex": {
                                            "type": "string"
                                          },
                                          "gt": {
                                            "type": "string"
                                          },
                                          "gte": {
                                            "type": "string"
                                          },
                                          "lte": {
                                            "type": "string"
                                          }
                                        },
                                        "additionalProperties": false
                                      },
                                      "customer.vatNumber": {
                                        "properties": {
                                          "lt": {
                                            "type": "string"
                                          },
                                          "ne": {
                                            "type": "string"
                                          },
                                          "eq": {
                                            "type": "string"
                                          },
                                          "regex": {
                                            "type": "string"
                                          },
                                          "gt": {
                                            "type": "string"
                                          },
                                          "gte": {
                                            "type": "string"
                                          },
                                          "lte": {
                                            "type": "string"
                                          }
                                        },
                                        "additionalProperties": false
                                      },
                                      "meta.href": {
                                        "properties": {
                                          "lt": {
                                            "type": "string"
                                          },
                                          "ne": {
                                            "type": "string"
                                          },
                                          "eq": {
                                            "type": "string"
                                          },
                                          "regex": {
                                            "type": "string"
                                          },
                                          "gt": {
                                            "type": "string"
                                          },
                                          "gte": {
                                            "type": "string"
                                          },
                                          "lte": {
                                            "type": "string"
                                          }
                                        },
                                        "additionalProperties": false
                                      },
                                      "customer.meta.id": {
                                        "properties": {
                                          "lt": {
                                            "type": "string"
                                          },
                                          "ne": {
                                            "type": "string"
                                          },
                                          "eq": {
                                            "type": "string"
                                          },
                                          "regex": {
                                            "type": "string"
                                          },
                                          "gt": {
                                            "type": "string"
                                          },
                                          "gte": {
                                            "type": "string"
                                          },
                                          "lte": {
                                            "type": "string"
                                          }
                                        },
                                        "additionalProperties": false
                                      }
                                    },
                                    "type": "object",
                                    "additionalProperties": false
                                  }
                                },
                                "type": "object",
                                "additionalProperties": false
                              }
                            }
                          },
                          "required": true
                        }
                      }
                    },
                    "/customers/getCollection": {
                      "post": {
                        "responses": {
                          "200": {
                            "content": {
                              "application/json": {
                                "schema": {
                                  "properties": {
                                    "items": {
                                      "items": {
                                        "properties": {
                                          "name": {
                                            "type": "string"
                                          },
                                          "meta": {
                                            "properties": {
                                              "id": {
                                                "type": "string"
                                              },
                                              "href": {
                                                "type": "string"
                                              }
                                            },
                                            "additionalProperties": false
                                          },
                                          "vatNumber": {
                                            "type": "string"
                                          }
                                        },
                                        "type": "object",
                                        "additionalProperties": false
                                      },
                                      "type": "array"
                                    }
                                  },
                                  "type": "object",
                                  "additionalProperties": false
                                }
                              }
                            },
                            "description": "OK"
                          }
                        },
                        "tags": [
                          "customers"
                        ],
                        "requestBody": {
                          "content": {
                            "application/json": {
                              "schema": {
                                "properties": {
                                  "offset": {
                                    "type": "integer"
                                  },
                                  "limit": {
                                    "type": "integer"
                                  },
                                  "orderBy": {
                                    "items": {
                                      "pattern": "^(name( asc| desc)?|meta[.]id( asc| desc)?|meta[.]href( asc| desc)?|vatNumber( asc| desc)?)$",
                                      "type": "string"
                                    },
                                    "type": "array"
                                  },
                                  "filter": {
                                    "properties": {
                                      "meta.id": {
                                        "properties": {
                                          "lt": {
                                            "type": "string"
                                          },
                                          "ne": {
                                            "type": "string"
                                          },
                                          "eq": {
                                            "type": "string"
                                          },
                                          "regex": {
                                            "type": "string"
                                          },
                                          "gt": {
                                            "type": "string"
                                          },
                                          "gte": {
                                            "type": "string"
                                          },
                                          "lte": {
                                            "type": "string"
                                          }
                                        },
                                        "additionalProperties": false
                                      },
                                      "name": {
                                        "properties": {
                                          "lt": {
                                            "type": "string"
                                          },
                                          "ne": {
                                            "type": "string"
                                          },
                                          "eq": {
                                            "type": "string"
                                          },
                                          "regex": {
                                            "type": "string"
                                          },
                                          "gt": {
                                            "type": "string"
                                          },
                                          "gte": {
                                            "type": "string"
                                          },
                                          "lte": {
                                            "type": "string"
                                          }
                                        },
                                        "additionalProperties": false
                                      },
                                      "meta.href": {
                                        "properties": {
                                          "lt": {
                                            "type": "string"
                                          },
                                          "ne": {
                                            "type": "string"
                                          },
                                          "eq": {
                                            "type": "string"
                                          },
                                          "regex": {
                                            "type": "string"
                                          },
                                          "gt": {
                                            "type": "string"
                                          },
                                          "gte": {
                                            "type": "string"
                                          },
                                          "lte": {
                                            "type": "string"
                                          }
                                        },
                                        "additionalProperties": false
                                      },
                                      "vatNumber": {
                                        "properties": {
                                          "lt": {
                                            "type": "string"
                                          },
                                          "ne": {
                                            "type": "string"
                                          },
                                          "eq": {
                                            "type": "string"
                                          },
                                          "regex": {
                                            "type": "string"
                                          },
                                          "gt": {
                                            "type": "string"
                                          },
                                          "gte": {
                                            "type": "string"
                                          },
                                          "lte": {
                                            "type": "string"
                                          }
                                        },
                                        "additionalProperties": false
                                      }
                                    },
                                    "type": "object",
                                    "additionalProperties": false
                                  }
                                },
                                "type": "object",
                                "additionalProperties": false
                              }
                            }
                          },
                          "required": true
                        }
                      }
                    }
                  },
                  "openapi": "3.1.0"
                }        
                """;
    }
}
