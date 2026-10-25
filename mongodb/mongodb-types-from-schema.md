To make MongoJsonDB become effective we need to use the right types in the DB.

I want the Json2BsonTranslator to use JSON Schema (extended with some x-dbtype per subschema) to translate JsonValue objects to BsonDocuments that fits with MongoDB 

It is important that this implementation is as fast as it is today in runtime. I belive the best way would be to walk through the JSON Schema as each field/index if the JsonObject/JsonArray is translated to BSON. 

The structure should be as in this example:

```
{
    "type": "object",
    "properties": {
        "id":{
            "type": "string",
            "x-dbtype": "ObjectId"  // This has to be set explicitly because there is no format that defaults to ObjectId
        },
        "name": {
            "type": "string" // this should default to string in Bson
        },
        "validUntil": {
            "type": "string",
            "format": "date",   // This means x-dbtype is Date by default
            "x-dbtype": "Date"
        },
        "propabilityOfSuccess": {
            "type": "number",  // This means Decimal128 by default
            "x-dbtype": "Decimal128"
        },
        "age": {
            "type": "integer", // defaults to 64 bit decimal
            "format": "int32", // int32 -> 32-bit integer, int64 -> 64-bit integer 
                
        }
        
    }

}
```

I want the solution to first translate the JSON Schema to en explcit schema DatabaseTypedSchema (can be cached as a special object that can be fetched by theJsonSchemaObject.as(DatabaseTypedSchema.class)). That way it is possible to check how defaults are applied by doing theDatabaseTypedSchema.asJson(). This mean the DatabaseTypedSchema.getJsonValue() would give a JsonValue that conatins all fields and nested structures as the theJsonSchemaObject but wwith x-dbtype applied for every property leaf.

It is only the root schema that should be used (or the schema references from the root - i.e $ref), If there is no root then the first schema of allOf should be used. Only properties and items will be investigated for x-dbtype (no other sub schemas will be investigated)

Make test cases for all mappings that you see fit this idea (as you understand from the example)

It is also important that other fields like filters etc use the database types that are used when storing in the corresponding attributes of the data objects that are defined in the schema. This should means there is a DatabaseTypedSchema for each collection. 

It may be possible to have more than one "x-dbtype" per property just as it is possible to have many types in a JSON schema. When using such multi x-dbtype schema to persist in DB, the first element in the x-dbtype array should be used. For queries/filters etc all types should be tried (that means an OR should be applied wherever the collections DatabaseTypedSchema)

Maybe MongoJsonDB should ceate (and cache) one Json2BsonTranslator per db and collection and use that specific Json2BsonTranslator. An something similar for translating the JSON query parameters to the right BSON (Based on the DatabaseTypedSchema) inside MongoJsonDB.  