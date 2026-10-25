package com.zuunr.mongodb;

import com.zuunr.json.JsonNumber;
import com.zuunr.json.JsonValue;
import org.bson.types.Decimal128;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Date;

/**
 * Converts one non-null JSON leaf value to the BSON value its schema's "x-dbtype"
 * (explicit or defaulted) resolved to. Used on the hot translation path, so every
 * case here is a direct conversion - no schema lookups happen at this point, those
 * already happened once when the {@link DatabaseTypedSchema} was built.
 */
final class Json2BsonConverter {

    private Json2BsonConverter() {
    }

    static Object toBson(JsonValue value, BsonType bsonType) {
        return switch (bsonType) {
            case STRING -> value.getString();
            case OBJECT_ID -> value.as(MongoObjectId.class).getObjectId();
            case DATE -> toDate(value.getString());
            case DECIMAL128 -> new Decimal128(value.getJsonNumber().asBigDecimal());
            case DOUBLE -> value.getJsonNumber().doubleValue();
            case INT32 -> toInt32(value.getJsonNumber());
            case INT64 -> value.getJsonNumber().longValue();
            case BOOLEAN -> value.getBoolean();
        };
    }

    private static int toInt32(JsonNumber number) {
        long longValue = number.longValue();
        if (longValue < Integer.MIN_VALUE || longValue > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Value " + longValue + " does not fit in x-dbtype \"Int32\"");
        }
        return (int) longValue;
    }

    /**
     * Accepts both JSON Schema {@code format: date} ("2024-01-01") and
     * {@code format: date-time} ("2024-01-01T10:00:00Z") strings, since both default
     * to x-dbtype "Date" and a leaf only tracks the resolved {@link BsonType}, not the
     * format that produced it.
     */
    private static Date toDate(String value) {
        try {
            return Date.from(Instant.parse(value));
        } catch (DateTimeParseException e) {
            try {
                return Date.from(LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant());
            } catch (DateTimeException e2) {
                throw new IllegalArgumentException("Value \"" + value + "\" is not a valid date or date-time for x-dbtype \"Date\"", e2);
            }
        }
    }
}
