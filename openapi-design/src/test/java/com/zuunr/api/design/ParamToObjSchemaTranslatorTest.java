package com.zuunr.api.design;

import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonValueFactory;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.MatcherAssert.*;


public class ParamToObjSchemaTranslatorTest {

    String parametersString = """
            [
              {
                "schema": {
                  "type": "string"
                },
                "name": "filter.parent.name.eq",
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
                "name": "filter.id.eq",
                "required": false,
                "in": "query"
              },
              {
                "schema": {
                  "type": "string"
                },
                "name": "filter.id.gt",
                "required": false,
                "in": "query"
              },
              {
                "schema": {
                  "type": "string"
                },
                "name": "filter.href.eq",
                "required": false,
                "in": "query"
              },
              {
                "schema": {
                  "type": "string"
                },
                "name": "filter.href.gt",
                "required": true,
                "in": "query"
              },
              {
                "schema": {
                  "items": {
                    "pattern": "^(name( asc| desc)?|id( asc| desc)?|href( asc| desc)?|name( asc| desc)?|colors( asc| desc)?)$",
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
            """;

    String expectdSchema = """
            {
               "properties": {
                 "orderBy": {
                   "items": {
                     "pattern": "^(name( asc| desc)?|id( asc| desc)?|href( asc| desc)?|name( asc| desc)?|colors( asc| desc)?)$",
                     "type": "string"
                   },
                   "type": "array"
                 },
                 "filter": {
                   "properties": {
                     "id": {
                       "properties": {
                         "eq": {
                           "type": "string"
                         },
                         "gt": {
                           "type": "string"
                         }
                       },
                       "additionalProperties": false
                     },
                     "href": {
                       "properties": {
                         "eq": {
                           "type": "string"
                         },
                         "gt": {
                           "type": "string"
                         }
                       },
                       "required": [
                         "gt"
                       ],
                       "additionalProperties": false
                     },
                     "name": {
                       "properties": {
                         "regex": {
                           "type": "string"
                         }
                       },
                       "additionalProperties": false
                     },
                     "parent.name": {
                       "properties": {
                         "eq": {
                           "type": "string"
                         }
                       },
                       "additionalProperties": false
                     }
                   },
                   "required": [
                     "href"
                   ],
                   "type": "object",
                   "additionalProperties": false
                 }
               },
               "type": "object",
               "additionalProperties": false
             }
            """;

    @Test
    void test() {
        JsonArray parameters = JsonValueFactory.create(parametersString).getJsonArray();
        assertThat(ParamToObjSchemaTranslator.translate(parameters).asJsonValue(), Matchers.is(JsonValueFactory.create(expectdSchema)));
    }
}
