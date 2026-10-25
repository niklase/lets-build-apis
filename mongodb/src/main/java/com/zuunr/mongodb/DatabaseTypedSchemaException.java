package com.zuunr.mongodb;

/**
 * Thrown while building a {@link DatabaseTypedSchema} when the JSON Schema cannot be
 * resolved to database types, e.g. an unknown or incompatible "x-dbtype", or a schema
 * shape this translator does not support (tuple-style "items", more than one non-null
 * "type", "x-dbtype" on an object/array).
 */
public class DatabaseTypedSchemaException extends RuntimeException {

    public DatabaseTypedSchemaException(String message) {
        super(message);
    }
}
