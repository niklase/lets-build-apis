package com.zuunr.mongodb;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.util.Date;

/**
 * <p>Converts {@link Date} (BSON's native date type) to an ISO-8601 instant string -
 * without this, Jackson's default Date handling writes epoch milliseconds as a plain
 * number instead. That matters here because it's the same shape the write-path
 * "Date" case (see Json2BsonConverter) parses back in, so a "format: date-time"/
 * "x-dbtype: Date" field round-trips correctly through a read followed by a write -
 * e.g. a PATCH that merges the current (just-read) state into the new state before
 * writing it back. Without this serializer, that round-trip fails with a
 * ClassCastException the moment a Date field's value - now a JsonNumber instead of a
 * JsonValue string - reaches the write-path coercion again.</p>
 *
 * @author Niklas Eldberger
 */
public class DateSerializer extends JsonSerializer<Date> {

    @Override
    public void serialize(Date value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeString(value.toInstant().toString());
    }
}
