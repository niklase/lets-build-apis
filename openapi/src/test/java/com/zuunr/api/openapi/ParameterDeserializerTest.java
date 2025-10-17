package com.zuunr.api.openapi;

import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonObjectBuilder;
import com.zuunr.json.JsonValue;
import com.zuunr.json.schema.Keywords;
import com.zuunr.jsontester.GivenWhenThenTester;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class ParameterDeserializerTest extends GivenWhenThenTester {

    private static OAS3Deserializer oas3Deserializer = new OAS3Deserializer();

    @Test
    void test_age_as_string_explode() {
        executeTest();
    }

    @Test
    void test_age_as_integer_explode() {
        executeTest();
    }

    @Test
    void test_age_as_integer() {
        executeTest();
    }

    @Test
    void test_age_as_string() {
        executeTest();
    }

    @Test
    void test_unrequired_parameter_missing() {
        executeTest();
    }

    @Test
    void test_age_as_string_array_without_explode() {
        executeTest();
    }

    @Test
    void test_age_as_integer_array_without_explode() {
        executeTest();
    }

    @Test
    void test_body_as_application_json() {
        executeTest();
    }

    @Test
    void test_bad_body_application_json() {
        executeTest();
    }

    @Test
    void test_bad_content_type_application_x_www_from_urlencoded() {
        executeTest();
    }

    @Test
    void test_bad_content_type_application_json() {
        executeTest();
    }

    @Test
    void test_bad_body_of_json_as_application_url_encoded_form() {
        executeTest();
    }

    @Test
    void test_body_as_application_url_encoded_form() {
        executeTest();
    }

    @Test
    void test_body_with_different_types_as_application_url_encoded_form() {
        executeTest();
    }

    @Test
    void test_body_as_application_url_encoded_form_list() {
        executeTest();
    }

    @Test
    void test_combination_1() {
        executeTest();
    }

    @Test
    void test_combination_body() {
        executeTest();
    }

    @Test
    void test_body_form_urlencoded_max_one_value() {
        executeTest();
    }

    @Test
    void test_body_form_urlencoded_max_two_values() {
        executeTest();
    }

    @Test
    void test_bad_combination_1() {
        executeTest();
    }

    @Test
    void test_boolean_instead_of_integer() {
        executeTest();
    }

    @Test
    void test_unsupported_content_type() {
        executeTest();
    }


    @Override
    public JsonValue doGivenWhen(JsonValue given, JsonValue when) {
        JsonObject operationObject = given.get("oasOperationObject").getJsonObject();
        return oas3Deserializer.deserializeRequest(when.getJsonObject(), operationObject).jsonValue();
    }


    @Test
    void toJsonSchemaTest() {
        JsonArray config = JsonArray.of(JsonObject.EMPTY
                .put("name", "name")
                .put("in", "query")
                .put("style", "form")
                .put("explode", false)
                .put("required", false)
                .put("allowReserved", false)
                .put("schema", JsonObject.EMPTY
                        .put("type", "integer")));

        JsonValue schema = toJsonSchema(config, true);
        // assertThat(schema, is(false));
    }

    private JsonValue toJsonSchema(JsonArray oas3parameters, boolean itemsTypeAlwaysString) {
        JsonObjectBuilder builder = JsonObject.EMPTY.builder();

        JsonObject query = JsonObject.EMPTY.put(Keywords.TYPE, "object").put(Keywords.ADDITIONAL_PROPERTIES, false);

        for (int i = 0; i < oas3parameters.size(); i++) {
            JsonValue parameter = oas3parameters.get(i);
            JsonValue maxItems = parameter.get("explode", true).getBoolean() ? null : JsonValue.ONE;

            if ("query".equals(parameter.get("in").getString())) {

                JsonValue name = parameter.get("name");
                JsonObjectBuilder propertyValueBuilder = JsonObject.EMPTY.builder()
                        .put(Keywords.TYPE, "array")
                        .put(Keywords.ITEMS, JsonObject.EMPTY
                                .put(Keywords.TYPE, itemsTypeAlwaysString
                                        ? "string"
                                        : parameter.get("schema", JsonObject.EMPTY).getJsonObject()
                                        .get(Keywords.ITEMS, parameter.get("schema", JsonObject.EMPTY))
                                        .get(Keywords.TYPE, Keywords.STRING).getString()));

                if (maxItems != null) {
                    propertyValueBuilder.put(Keywords.MAX_ITEMS, maxItems);
                }

                if (parameter.get("required").getBoolean()) {
                    query = query.put(Keywords.REQUIRED, query.get(Keywords.REQUIRED, JsonArray.EMPTY).getJsonArray().add(name));
                }
                query = query
                        .put(JsonArray.of(Keywords.PROPERTIES, name), propertyValueBuilder.build());
            }
        }
        return JsonObject.EMPTY
                .put(Keywords.TYPE, "object")
                .put(Keywords.ADDITIONAL_PROPERTIES, false)
                .put(Keywords.PROPERTIES, JsonObject.EMPTY.put("query", query))
                .jsonValue();
    }

    @Test
    void parseQueryStringToJsonArray() {
        assertThat(
                OAS3Deserializer.parseQueryStringToMultiValueJsonObject("query", JsonObject.EMPTY.put("request", JsonObject.EMPTY.put("query", "name=Peter&age=27&name=Andersson"))),
                is(JsonObject.EMPTY
                        .put("name", JsonArray.of("Peter", "Andersson"))
                        .put("age", JsonArray.of("27"))));
    }
}
