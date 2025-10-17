package com.zuunr.api.design.controller;

import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValueFactory;

public class InMemDB {

    public static JsonObject infoModel = JsonValueFactory.create("""
                {
                      "$defs": {
                          "customers": {
                              "collection": {
                                  "properties": {
                                      "items": {
                                          "items": {
                                              "$ref": "#/$defs/customers/item"
                                          },
                                          "type": "array"
                                      }
                                  },
                                  "type": "object"
                              },
                              "item": {
                                  "properties": {
                                      "name": {
                                          "type": "string"
                                      },
                                      "meta": {
                                          "$ref": "#/$defs/meta/item"
                                      },
                                      "vatNumber": {
                                          "type": "string"
                                      }
                                  },
                                  "type": "object",
                                  "additionalProperties": false
                              }
                          },
                          "meta": {
                              "collection": {
                                  "properties": {
                                      "next": {
                                          "properties": {
                                              "meta": {
                                                  "$ref": "#/$defs/meta/collection"
                                              }
                                          }
                                      },
                                      "href": {
                                          "type": "string"
                                      },
                                      "prev": {
                                          "properties": {
                                              "meta": {
                                                  "$ref": "#/$defs/meta/collection"
                                              }
                                          }
                                      }
                                  }
                              },
                              "item": {
                                  "properties": {
                                      "id": {
                                          "type": "string"
                                      },
                                      "href": {
                                          "type": "string"
                                      }
                                  }
                              }
                          },
                          "orders": {
                              "collection": {
                                  "properties": {
                                      "items": {
                                          "items": {
                                              "$ref": "#/$defs/orders/item"
                                          },
                                          "type": "array"
                                      }
                                  },
                                  "type": "object",
                                  "examples": [
                                      {
                                          "items": [
                                              {
                                                  "quantity": 200,
                                                  "productName": "Tomatoes",
                                                  "meta": {
                                                      "id": "defaf7657ef",
                                                      "href": "https://api.example.com/orders/defaf7657ef"
                                                  },
                                                  "customer": {
                                                      "name": "Peters Resturant",
                                                      "meta": {
                                                          "id": "ddd54443ef",
                                                          "href": "https://api.example.com/customers/f765defa7ef"
                                                      },
                                                      "vatNumber": "55010203123401"
                                                  }
                                              },
                                              {
                                                  "quantity": 5,
                                                  "productName": "Tomatoes",
                                                  "meta": {
                                                      "id": "555df23f1",
                                                      "href": "https://api.example.com/orders/555df23f1"
                                                  },
                                                  "customer": {
                                                      "name": "Small Company AB",
                                                      "meta": {
                                                          "id": "f2f54443ef",
                                                          "href": "https://api.example.com/customers/f2f54443ef"
                                                      },
                                                      "vatNumber": "55221103663201"
                                                  }
                                              }
                                          ]
                                      }
                                  ]
                              },
                              "item": {
                                  "properties": {
                                      "quantity": {
                                          "type": "integer",
                                          "description": "The number of ordered products"
                                      },
                                      "productName": {
                                          "type": "string"
                                      },
                                      "meta": {
                                          "$ref": "#/$defs/meta/item"
                                      },
                                      "customer": {
                                          "$ref": "#/$defs/customers/item"
                                      }
                                  },
                                  "type": "object",
                                  "additionalProperties": false,
                                  "examples": [
                                      {
                                          "quantity": 5,
                                          "productName": "Tomatoes",
                                          "meta": {
                                              "id": "555df23f1",
                                              "href": "https://api.example.com/orders/555df23f1"
                                          },
                                          "customer": {
                                              "name": "Small Company AB",
                                              "meta": {
                                                  "id": "f2f54443ef",
                                                  "href": "https://api.example.com/customers/f2f54443ef"
                                              },
                                              "vatNumber": "55221103663201"
                                          }
                                      }
                                  ]
                              }
                          }
                      }
                  }
            """).getJsonObject();
    public static JsonObject initialOpenApiDoc = JsonValueFactory.create("""
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
}
