package com.zuunr.api.openapi;

import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import com.zuunr.json.pointer.JsonPointer;
import com.zuunr.json.schema.Keywords;
import com.zuunr.json.schema.generation.SchemaGenerator;
import org.junit.jupiter.api.Test;

import static com.zuunr.api.openapi.AdditionalPropertiesApplicator.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class AdditionalPropertiesApplicatorTest {

    @Test
    void test1() {
        assertThat(
                updateAdditionalProperties(JsonObject.EMPTY.put("type", "object"), JsonValue.FALSE).get(Keywords.ADDITIONAL_PROPERTIES),
                is(JsonValue.FALSE)
        );
    }

    @Test
    void testApplyFalse() {

        JsonObject schema = new SchemaGenerator().generateSchema(JsonObject.EMPTY
                .put("name", "Peter")
                .put("contacts", JsonArray.of(
                        JsonObject.EMPTY
                                .put("name", "Ann").put("age", 99))));


        JsonObject result = updateAdditionalProperties(schema, JsonValue.FALSE);
        assertThat(result.get(Keywords.ADDITIONAL_PROPERTIES), is(JsonValue.FALSE));
        assertThat(result.get(JsonPointer.of("/properties/contacts/items/additionalProperties")), is(JsonValue.FALSE));


    }

    @Test
    void testApplyTrue() {

        JsonObject schema = new SchemaGenerator().generateSchema(JsonObject.EMPTY
                .put("name", "Peter")
                .put("contacts", JsonArray.of(
                        JsonObject.EMPTY
                                .put("name", "Ann").put("age", 99))));


        JsonObject result = updateAdditionalProperties(schema, JsonValue.TRUE);
        assertThat(result.get(Keywords.ADDITIONAL_PROPERTIES), is(JsonValue.TRUE));
        assertThat(result.get(JsonPointer.of("/properties/contacts/items/additionalProperties")), is(JsonValue.TRUE));
    }
}
