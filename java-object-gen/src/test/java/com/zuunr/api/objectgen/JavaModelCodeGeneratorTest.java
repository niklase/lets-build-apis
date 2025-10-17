package com.zuunr.api.objectgen;

import com.zuunr.json.JsonArray;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValueFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;


public class JavaModelCodeGeneratorTest {
    @Test
    void classNameTest1() {
        JsonArray pathToSchema = JsonArray.of("paths", "/admin/users", "post", "requestBody", "content", "application/json", "schema");
        JsonObject openApiDoc = JsonObject.EMPTY.put(pathToSchema, JsonObject.EMPTY.put("type", "object"));
        String name = JavaModelCodeGenerator.createJavaClassName(pathToSchema, openApiDoc, "com.example");
        assertEquals("com.example._admin_users.post.RequestBody", name);
    }

    @Test
    void classNameTest2() {
        JsonArray pathToSchema = JsonArray.of("paths", "/admin/users", "post", "requestBody", "content", "application/json", "schema");
        JsonObject openApiDoc = JsonObject.EMPTY.put(pathToSchema, JsonObject.EMPTY.put("type", "string"));
        String name = JavaModelCodeGenerator.createJavaClassName(pathToSchema, openApiDoc, "com.example");
        assertEquals("String", name);
    }


    @Test
    void createJavaClasses1() {
        JsonArray pathToSchema = JsonArray.of("paths", "/admin/users", "post", "requestBody", "content", "application/json", "schema");
        JsonObject openApiDoc = JsonObject.EMPTY.put(pathToSchema, JsonObject.EMPTY.put("type", "string"));
        JsonObject javaClasses = JavaModelCodeGenerator.createJavaClasses(openApiDoc, "com.example.myapi");
        assertEquals(JsonObject.EMPTY, javaClasses);
    }

    @Test
    void createJavaClasses2() {
        JsonArray pathToSchema = JsonArray.of("paths", "/admin/users", "post", "requestBody", "content", "application/json", "schema");
        JsonObject openApiDoc = JsonObject.EMPTY.put(pathToSchema, JsonObject.EMPTY
                .put("type", "object")
                .put("properties", JsonObject.EMPTY
                        .put("prop1", JsonObject.EMPTY
                                .put("type", "string"))));
        JsonObject javaClasses = JavaModelCodeGenerator.createJavaClasses(openApiDoc, "io.someco.restapi");

        String javaClass = """
                package io.someco.restapi._admin_users.post;
                
                public class RequestBody {
                    public final String prop1;
                
                    public final String getProp1() {
                        return prop1;
                    }
                }""";

        assertEquals(JsonObject.EMPTY.put("io.someco.restapi._admin_users.post.RequestBody", javaClass), javaClasses);
    }

    @Test
    void createJavaClasses3() {
        JsonArray pathToSchema = JsonArray.of("paths", "/admin/users", "post", "requestBody", "content", "application/json", "schema");
        JsonObject openApiDoc = JsonObject.EMPTY.put(pathToSchema, JsonObject.EMPTY
                .put("type", "object")
                .put("properties", JsonObject.EMPTY
                        .put("prop1", JsonObject.EMPTY
                                .put("type", "object")
                                .put("properties", JsonObject.EMPTY.put("prop2", JsonObject.EMPTY.put("type", "string"))))));
        JsonObject javaClasses = JavaModelCodeGenerator.createJavaClasses(openApiDoc, "com.example");

        String javaClass1 = """
                package com.example._admin_users.post;
                
                public class RequestBody {
                    public final com.example._admin_users.post.RequestBody_prop1 prop1;
                
                    public final com.example._admin_users.post.RequestBody_prop1 getProp1() {
                        return prop1;
                    }
                }""";

        String javaClass2 = """
                package com.example._admin_users.post;
                
                public class RequestBody_prop1 {
                    public final String prop2;
                
                    public final String getProp2() {
                        return prop2;
                    }
                }""";

        assertEquals(JsonObject.EMPTY
                        .put("com.example._admin_users.post.RequestBody", javaClass1)
                        .put("com.example._admin_users.post.RequestBody_prop1", javaClass2),
                javaClasses);
    }
    @Test
    void createJavaClasses4() {
        JsonArray pathToSchema = JsonArray.of("paths", "/admin/users", "post", "requestBody", "content", "application/json", "schema");
        JsonObject openApiDoc = JsonObject.EMPTY
                .put(pathToSchema, JsonObject.EMPTY
                        .put("type", "object")
                        .put("properties", JsonObject.EMPTY
                                .put("friends", JsonObject.EMPTY
                                        .put("type", "array")
                                        .put("items", JsonObject.EMPTY
                                                .put("type", "object")
                                                .put("properties", JsonObject.EMPTY
                                                        .put("firstName", JsonObject.EMPTY.put("type", "string"))
                                                )

                                        ))));
        JsonObject classes = JavaModelCodeGenerator.createJavaClasses(openApiDoc, "com.example");

        String expectedJson = """
                {
                  "com.example._admin_users.post.RequestBody_friends_items": "package com.example._admin_users.post;\\n\\npublic class RequestBody_friends_items {\\n    public final String firstName;\\n\\n    public final String getFirstName() {\\n        return firstName;\\n    }\\n}",
                  "com.example._admin_users.post.RequestBody": "package com.example._admin_users.post;\\n\\npublic class RequestBody {\\n    public final List<com.example._admin_users.post.RequestBody_friends_items> friends;\\n\\n    public final List<com.example._admin_users.post.RequestBody_friends_items> getFriends() {\\n        return friends;\\n    }\\n}"
                }
                """;

        assertEquals(JsonValueFactory.create(expectedJson), classes.jsonValue());
    }
}
