package com.zuunr.mongodb;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The BSON types that an "x-dbtype" schema keyword is allowed to select, and the
 * single string token used to spell each one in a JSON Schema document.
 */
public enum BsonType {

    STRING("String"),
    OBJECT_ID("ObjectId"),
    DATE("Date"),
    DECIMAL128("Decimal128"),
    DOUBLE("Double"),
    INT32("Int32"),
    INT64("Int64"),
    BOOLEAN("Boolean");

    private static final Map<String, BsonType> BY_X_DBTYPE = Stream.of(values())
            .collect(Collectors.toMap(BsonType::xDbType, type -> type));

    private final String xDbType;

    BsonType(String xDbType) {
        this.xDbType = xDbType;
    }

    /**
     * @return the exact string this type is spelled as in an "x-dbtype" schema value.
     */
    public String xDbType() {
        return xDbType;
    }

    /**
     * @throws DatabaseTypedSchemaException if the token is not one of the known "x-dbtype" values.
     */
    public static BsonType fromXDbType(String xDbType) {
        BsonType type = BY_X_DBTYPE.get(xDbType);
        if (type == null) {
            throw new DatabaseTypedSchemaException("Unknown x-dbtype \"" + xDbType + "\". Supported values: "
                    + BY_X_DBTYPE.keySet().stream().sorted().collect(Collectors.joining(", ")));
        }
        return type;
    }
}
