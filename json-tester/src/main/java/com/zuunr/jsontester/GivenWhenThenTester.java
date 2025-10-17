package com.zuunr.jsontester;

import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonObjectFactory;
import com.zuunr.json.JsonObjectMerger;
import com.zuunr.json.JsonValue;


import static org.junit.jupiter.api.Assertions.assertEquals;


public abstract class GivenWhenThenTester {

    private static final String MERGE_ME = "mergeMe";

    JsonObjectFactory jsonObjectFactory = new JsonObjectFactory();
    JsonObjectMerger jsonObjectMerger = new JsonObjectMerger();

    protected void executeTest() {
        String methodName = new Throwable().getStackTrace()[1].getMethodName();
        Class<?> thisClass = this.getClass();
        JsonObject testCase = jsonObjectFactory.createJsonObject(thisClass.getResourceAsStream(methodName + ".json"));

        JsonValue result = doGivenWhen(testCase.get("given"), testCase.get("when"));
        JsonValue then = testCase.get("then");

        if (Boolean.TRUE == testCase.get("exactMatch", true).getBoolean()) {
            assertEquals(result, then, methodName);
        } else {
            JsonObject thenToBeMerged = JsonObject.EMPTY.put(MERGE_ME, then);
            JsonObject resultToBeMerged = JsonObject.EMPTY.put(MERGE_ME, result);
            JsonObject thenMergedByResult = jsonObjectMerger.merge(thenToBeMerged, resultToBeMerged);
            JsonObject resultMergedByThen = jsonObjectMerger.merge(resultToBeMerged, thenToBeMerged);

            if (!resultMergedByThen.get(MERGE_ME).equals(result)) {
                assertEquals(result, then);
            }
            assertEquals(thenMergedByResult.get(MERGE_ME), resultMergedByThen.get(MERGE_ME));
        }
    }

    /**
     * Should return the value of given when
     *
     * @param given
     * @param when
     * @return
     */
    public abstract JsonValue doGivenWhen(JsonValue given, JsonValue when);


}
