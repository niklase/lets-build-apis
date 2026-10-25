package com.zuunr.mongodb;

import com.zuunr.json.JsonObject;
import com.zuunr.json.util.JsonObjectWrapper;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the gap that broke a PATCH round-trip in practice: a BSON Date read back
 * from Mongo must deserialize to a JSON string, not Jackson's default epoch-millis
 * number - otherwise merging the read-back "currentState" into a "newState" and
 * writing it back crashes the schema-aware write path (which expects a string for any
 * x-dbtype "Date" field) with a ClassCastException.
 */
class DateSerializerTest {

    private final ResourceDeserializer resourceDeserializer = new ResourceDeserializer().init();

    @Test
    @DisplayName("a BSON Date deserializes to an ISO-8601 string, not a number")
    void dateDeserializesToIsoString() {
        Instant instant = Instant.parse("2026-07-30T14:00:01.601Z");
        Document document = new Document("createdAt", Date.from(instant));

        JsonObject result = resourceDeserializer.deserialize(document, JsonObjectWrapper.class).asJsonObject();

        assertTrue(result.get("createdAt").isString(), "expected a JSON string, not a number");
        assertEquals(instant, Instant.parse(result.get("createdAt").getString()));
    }
}
