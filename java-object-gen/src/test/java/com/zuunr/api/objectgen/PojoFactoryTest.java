package com.zuunr.api.objectgen;

import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.PojoFactory;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class PojoFactoryTest {

    @Test
    void test() {
        PersonTestPojo testPojo = new PojoFactory().createPojo(JsonObject.EMPTY.put("name", "Peter"), PersonTestPojo.class);
        assertEquals("Peter", testPojo.getName());
        assertNull(testPojo.getAge());
    }

    @Test
    void listTest() {
        PersonTestPojo testPojo = new PojoFactory().createPojo(JsonObject.EMPTY.put("friends", JsonArray.of("Laura")), PersonTestPojo.class);
        ArrayList<String> expected = new ArrayList<>();
        expected.add("Laura");
        assertEquals(expected, testPojo.getFriends());
    }
}
