# CreateItemRequestHandler

Create a class com.zuunr.dcentb.rest.requesthandler.CreateItemRequestHandler that should handle a POST request to create an item in MongoDB that uses similar classes and methods as ReadCollectionRequestHandler does. There are methods in com.zuunr.mongodb.MongoJsonDB, com.zuunr.openapi.OAS3Deserializer that fits for creating.

The first processors in the CreateItemRequestHandler should be (as in the ReadCollectionRequestHandler, tell me explicitly if that you think that is a bad idea)

1. apiKeyAuthenticator,
2. oasRequestDeserializer,
3. userInfoProvider,
4. requestAccessController,
5. mongoJsonDBCommandCreator,

Request:
````

uri: /players
headers: {
    "Content-Type": ["application/json"]
    ...
}   
body:
{
    "property1": "value1",  
    "property2": "value2" 
}
````
Response:
````
status: 201
headers: {
    "location": "/players/UUIDGENERATED232345" 
}
body:
{
    "meta": {
        "id": "UUIDGENERATED232345" // Should be generated in Java code (not by mongoDB), should be the root _id of the document when stroed in mongoDB
        },
    "property1": "value1", 
    "property2": "value2" 
}
````