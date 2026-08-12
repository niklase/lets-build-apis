# Access control

There are three different roles in this API.

| Role/Permission name | Description |
|----------------------|-------------|
| `admin`              |is allowed to do everything that the API allows|
| `teacher`            |is allowed to update grade and attendence of his/her students|
| `student`            |is allowed to update email of himself/herself|

Api keys are used in this demo because it is an easy way to demonstrate authorization. Api keys which defined in `apiKeyGeneration` are generated if they are not present in database.

Other authentication mechanisms like OIDC or cookies can be used with the same athorization behavior as in the demo.

# Update operations

Updates are done by applying JSON Merge Patch on the current state of the entity data item with the JSON Object in the API request body of the PATCH operation.

# Business rules

Business rules as constraints for each entity data item type are specified as a JSON Schema. Both the current state (i.e the state before any Update or Delete operation) of an entity and the new state that is to be stored (in the case of Create or Update) is part of the model that is validated by the JSON Schema.

Current state in the case of a new entity (create with `POST`) is `null`. New state in the case of deleting an existing entity (`DELETE`) is `null`.  

In this demo API there is one business rule that only allows grade `F` when `attendencePercent` is less than `50`.

All rules are specified in the OpenAPI document.

