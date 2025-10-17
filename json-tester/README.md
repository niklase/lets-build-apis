# JSON Tester

## Declarative given-when-then test cases in JSON executed in JUnit

Write _one_ test method and declare all test cases in JSON

      your_project
      └─ src
         └─ main
            └─ test
               ├─ java
               │  └─ com
               │     └─ example
               │        └─ YourGivenWhenThenTest.java   (subclass of GivenWhenThenTesterBase.java)
               └─ resources
                  └─ com
                     └─ example
                        └─ YourGivenWhenThenTest  ( map/folder with same name as test class!)
                           ├─ test1.json
                           ├─ second_tst.json     (test files can be of any number and name but must have suffix ".json")
                           └─ other.json 


Example of test case asserting balance after withdrawal from a bank account

    {
        "given": {"balance": 270},   // initial state provided TO your test class
        "when": {"withdrawal": 240}  // event provided TO your test
        "then": {"balance": 30}      // result provided FROM your test class
    }


Example test class implementation:

    class AccountBalanceExampleTest extends GivenWhenThenTesterBase {

        @Override
        public JsonValue doGivenWhen(JsonValue given, JsonValue when) {
    
            // Perform the test 
            BigDecimal balance = given.get("balance").getJsonNumber().asBigDecimal();
            BigDecimal withdrawal = when.get("withdrawal").getJsonNumber().asBigDecimal();
            BigDecimal resultingBalance = balance.subtract(withdrawal);
            
            // Return result (i.e "then" in given-when-then) as JsonValue
            return JsonObject.EMPTY.put("balance", resultingBalance).jsonValue();
        }

        // Some code omitted for readability...
    }