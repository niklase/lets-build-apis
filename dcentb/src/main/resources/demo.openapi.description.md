# Access control

There are three different roles in this API.

| Role/Permission name | Description |
|----------------------|-------------|
| `admin`              |is allowed to do everything that the API allows|
| `teacher`            |is allowed to update grade and attendence of his/her students|
| `student`            |is allowed to update email of himself/herself|

Api keys are used in this demo because it is an easy way to demonstrate authorization. Api keys which defined in `apiKeyGeneration` are generated if they are not present in database.

Authentication mechanisms like OIDC or cookies can be used with the same athorization behavior as in the demo.

# Business rules
Business rules are constraints for the transition from a current state of an existing entity to a new state of that entity.

Current state in the case of a new entity (create with `POST`) is `null`. New state in the case of deleting an existing entity (`DELETE`) is `null`.  

In this demo API there is one business rule that only allows `grade` "F" for students with `attendencePercent` lower than `50`. 

