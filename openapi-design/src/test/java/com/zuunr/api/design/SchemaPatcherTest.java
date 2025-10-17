package com.zuunr.api.design;

import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValueFactory;
import com.zuunr.json.schema.JsonSchema;
import com.zuunr.json.schema.Keywords;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class SchemaPatcherTest {
    @Test
    void test1() {
        SchemaPatcher schemaPatcher = new SchemaPatcher();

        JsonObject original = JsonObject.EMPTY;
        JsonObject patch = JsonObject.EMPTY.put(Keywords.DESCRIPTION, "Hello");

        JsonObject result = schemaPatcher.patch(original.as(JsonSchema.class), patch.as(JsonSchema.class), 10

        ).asJsonValue().getJsonObject();
        MatcherAssert.assertThat(result, Matchers.is(patch));
    }

    @Test
    void oneProperty() {
        SchemaPatcher schemaPatcher = new SchemaPatcher();

        JsonObject original = JsonObject.EMPTY.put(Keywords.PROPERTIES, JsonObject.EMPTY.put("name", JsonObject.EMPTY));
        JsonObject patch = JsonObject.EMPTY

                .put(Keywords.ADDITIONAL_PROPERTIES, false).put(Keywords.PROPERTIES, JsonObject.EMPTY.put("name", JsonObject.EMPTY.put(Keywords.MAX_LENGTH, 100).put(Keywords.DESCRIPTION, "The full name of person")));

        JsonObject result = schemaPatcher.patch(original.as(JsonSchema.class), patch.as(JsonSchema.class), 10).asJsonValue().getJsonObject();
        MatcherAssert.assertThat(result, Matchers.is(patch));
    }

    @Test
    void onePropertyWithRef() {
        SchemaPatcher schemaPatcher = new SchemaPatcher();

        JsonObject original = JsonObject.EMPTY.put(Keywords.PROPERTIES, JsonObject.EMPTY.put("name", JsonObject.EMPTY));
        JsonObject patch = JsonObject.EMPTY

                .put(Keywords.ADDITIONAL_PROPERTIES, false).put(Keywords.PROPERTIES, JsonObject.EMPTY.put("name", JsonObject.EMPTY.put(Keywords.REF, "/$defs/Name"))).put(Keywords.DEFS, JsonObject.EMPTY.put("Name", JsonObject.EMPTY.put(Keywords.REF, "/$defs/PersonName")).put("PersonName", JsonObject.EMPTY.put(Keywords.MAX_LENGTH, 100).put(Keywords.DESCRIPTION, "The full name of person")));

        JsonObject result = schemaPatcher.patch(original.as(JsonSchema.class), patch.as(JsonSchema.class), 10).asJsonValue().getJsonObject();
        JsonObject expected = JsonObject.EMPTY

                .put(Keywords.ADDITIONAL_PROPERTIES, false).put(Keywords.PROPERTIES, JsonObject.EMPTY.put("name", JsonObject.EMPTY.put(Keywords.MAX_LENGTH, 100).put(Keywords.DESCRIPTION, "The full name of person")));
        MatcherAssert.assertThat(result, Matchers.is(expected));
    }

    @Test
    void twoPropertiesWithRefOnDifferentNestingLevels() {
        SchemaPatcher schemaPatcher = new SchemaPatcher();

        JsonObject original = JsonValueFactory.create("""
                {
                  "properties": {
                    "age": {
                      "$ref": "/$defs/Age"
                    },
                    "name": {}
                  },
                  "$defs": {
                    "Age": {}
                  }
                }
                """).getJsonObject();

        JsonObject patch = JsonValueFactory.create("""
                {
                  "properties": {
                    "age": {
                      "$ref": "/$defs/Age"
                    },
                    "name": {
                      "$ref": "/$defs/Name"
                    }
                  },
                  "$defs": {
                    "Age": {
                      "$ref": "/$defs/PersonAge"
                    },
                    "PersonAge": {
                      "minimum": 0,
                      "description": "The age of person"
                    },
                    "Name": {
                      "maxLength": 100,
                      "description": "The full name of person"
                    }
                  }
                }
                """).getJsonObject();


        JsonObject result1 = schemaPatcher.patch(original.as(JsonSchema.class), patch.as(JsonSchema.class), 10).asJsonValue().getJsonObject();
        JsonObject expected1 = JsonObject.EMPTY.put(Keywords.PROPERTIES, JsonObject.EMPTY.put("name", JsonObject.EMPTY.put(Keywords.MAX_LENGTH, 100).put(Keywords.DESCRIPTION, "The full name of person")).put("age", JsonObject.EMPTY.put(Keywords.MINIMUM, 0).put(Keywords.DESCRIPTION, "The age of person"))).put(Keywords.DEFS, JsonObject.EMPTY.put("Age", JsonObject.EMPTY)).put(Keywords.ADDITIONAL_PROPERTIES, false);
        MatcherAssert.assertThat(result1, Matchers.is(expected1));

        JsonObject result2 = schemaPatcher.patch(original.as(JsonSchema.class), patch.as(JsonSchema.class), 1).asJsonValue().getJsonObject();
        JsonObject expected2 = JsonObject.EMPTY
                .put(Keywords.ADDITIONAL_PROPERTIES, false)
                .put(Keywords.PROPERTIES, JsonObject.EMPTY
                        .put("name", JsonObject.EMPTY
                                .put(Keywords.MAX_LENGTH, 100)
                                .put(Keywords.DESCRIPTION, "The full name of person")))
                .put(Keywords.DEFS, JsonObject.EMPTY
                        .put("Age", JsonObject.EMPTY));
        MatcherAssert.assertThat(result2, Matchers.is(expected2));
    }


}
