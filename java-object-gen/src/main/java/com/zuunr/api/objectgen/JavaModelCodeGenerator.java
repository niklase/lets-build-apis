package com.zuunr.api.objectgen;

import com.zuunr.json.*;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;

import java.io.File;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URL;
import java.util.Objects;
import java.util.TimeZone;

public class JavaModelCodeGenerator {

    private static final Configuration cfg = new Configuration(Configuration.VERSION_2_3_34);

    static {
        try {
            // cfg.setDirectoryForTemplateLoading(getTemplateDirectory());
            cfg.setClassLoaderForTemplateLoading(ClassLoader.getSystemClassLoader(), "");
            // Recommended settings for new projects:
            cfg.setDefaultEncoding("UTF-8");
            cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
            cfg.setLogTemplateExceptions(false);
            cfg.setWrapUncheckedExceptions(true);
            cfg.setFallbackOnNullLoopVariable(false);
            //cfg.setAutoEscapingPolicy(Configuration.DISABLE_AUTO_ESCAPING_POLICY);
            cfg.setSQLDateAndTimeTimeZone(TimeZone.getDefault());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static File getTemplateDirectory() {
        // Get the URL of the root level of resources
        // URL resourceUrl = Thread.currentThread().getContextClassLoader().getResource("");
        URL resourceUrl = JavaModelCodeGenerator.class.getClassLoader().getResource("");
        Objects.requireNonNull(resourceUrl, "Resource directory not found!");

        // Convert the URL to a File object
        return new File(resourceUrl.getPath());
    }

    String schemaString = """
            {
                "type": "object",
                "properties": {
                    "name": { "type": "string" }
                },
                "additionalProperties": false
            }
            """;

    JsonObject schema = JsonValueFactory.create(schemaString).getJsonObject();

    public static JsonObject createJavaClasses(JsonObject openApiDoc, String basePackage) {

        JsonObject classesSoFar = JsonObject.EMPTY;
        JsonArray paths = openApiDoc.get("paths").getJsonObject().keys();
        for (int i = 0; i < paths.size(); i++) {
            classesSoFar = createClassesForOperation(JsonArray.of("paths", paths.get(i), "post"), openApiDoc, classesSoFar, basePackage);
        }
        return classesSoFar;
    }

    public static JsonObject createClassesForOperation(JsonArray operationPath, JsonObject openApiDoc, JsonObject classesSoFar, String basePackage) {
        JsonArray pathToJsonSchema = operationPath.addAll(JsonArray.of("requestBody", "content", "application/json", "schema"));
        return createJavaClasses(openApiDoc, pathToJsonSchema, classesSoFar, basePackage);
    }

    public static JsonObject createJavaClasses(JsonObject openApiDoc, JsonArray pathToSchema, JsonObject classesSoFar, String basePackage) {

        try {
            JsonObject classModel = JsonObject.EMPTY;

            JsonObject schema = openApiDoc.get(pathToSchema).getJsonObject();
            String javaLangType = getJavaLangType(pathToSchema, openApiDoc, basePackage);
            if (javaLangType != null) {
                return classesSoFar;
            }

            String javaClassName = createJavaClassName(pathToSchema, openApiDoc, basePackage);
            if (classesSoFar.containsKey(javaClassName)) {
                return classesSoFar;
            }

            if (schema.get("type").getString().equals("object")) {
                // Handle JSON Schema for object type
                JsonArray orderedProperties = schema.get("properties").getJsonObject().keys().sort();
                JsonArrayBuilder builder = JsonArray.EMPTY.builder();
                for (JsonValue key : orderedProperties) {
                    JsonObject fieldObject = JsonObject.EMPTY
                            .put("name", key.getString())
                            .put("className", createJavaClassName(pathToSchema.add("properties").add(key), openApiDoc, basePackage));
                    builder.add(fieldObject);
                }
                JsonArray fields = builder.build();
                if (!fields.isEmpty()) {
                    classModel = classModel.put("fields", fields);
                }
                classModel = classModel
                        .put("package", javaClassName.replaceAll("[.][^.]*$", ""))
                        .put("className", javaClassName.substring(javaClassName.lastIndexOf('.') + 1));
                for (JsonValue key : orderedProperties) {
                    classesSoFar = classesSoFar.putAll(createJavaClasses(openApiDoc, pathToSchema.add("properties").add(key), classesSoFar, basePackage));
                }
            } else if (schema.get("type").getString().equals("array")) {
                // Handle JSON Schema for array type
                JsonObject itemsSchema = schema.get("items").getJsonObject();

                // Create a class name for the items type
                String itemsClassName = createJavaClassName(
                        pathToSchema.add("items"), openApiDoc, basePackage);

                // Add a field representing the array with the items type
                JsonArrayBuilder builder = JsonArray.EMPTY.builder();
                JsonObject fieldObject = JsonObject.EMPTY
                        .put("name", "items")
                        .put("className", "List<" + itemsClassName + ">");
                builder.add(fieldObject);

                JsonArray fields = builder.build();
                classModel = classModel.put("fields", fields);

                // Update class package and name
                classModel = classModel
                        .put("package", javaClassName.replaceAll("[.][^.]*$", ""))
                        .put("className", javaClassName.substring(javaClassName.lastIndexOf('.') + 1));

                // Recursively generate class for items in the array, if it's a complex type
                if (itemsSchema.get("type").getString().equals("object") || itemsSchema.get("type").getString().equals("array")) {
                    classesSoFar = classesSoFar.putAll(createJavaClasses(
                            openApiDoc, pathToSchema.add("items"), classesSoFar, basePackage));
                }
                return classesSoFar; // List<> should not be a rendered class
            }
            Template temp = cfg.getTemplate("class.ftlh");

            /* Merge data-model with template */
            Writer out = new StringWriter();
            temp.process(classModel.asMapsAndLists(), out);
            return classesSoFar.put(javaClassName, out.toString());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static String createJavaClassName(JsonArray pathToSchema, JsonObject openApiDoc, String basePackage) {

        String javaLangType = getJavaLangType(pathToSchema, openApiDoc, basePackage);
        if (javaLangType != null) {
            return javaLangType;
        }

        JsonValue schemaType = openApiDoc.get(pathToSchema).get("type");
        if (schemaType.isString() && schemaType.getString().equals("array") || schemaType.isJsonArray() && schemaType.getJsonArray().contains("array")) {
            return "List<" + createJavaClassName(pathToSchema.add("items"), openApiDoc, basePackage) + ">";
        }

        StringBuilder builder = new StringBuilder();
        boolean objectProperties = false;
        for (int i = 0; i < pathToSchema.size(); i++) {

            if (i != 0) {
                builder.append('.');
            }
            String currentElement = pathToSchema.get(i).getString();
            String escaped = currentElement.replace("_", "__").replace(".", "_dot_").replace("/", "_").replace("-", "_");
            builder.append(escaped);
        }
        // builder.append(".").append(filePath.last().getString().substring(0, 1).toUpperCase()).append(filePath.last().getString().substring(1));
        String javaClass = builder.toString().replaceFirst("paths[.]([^.]*[.][^.]*[.]requestBody)[.]content[.]application_json[.]schema", "$1");

        javaClass = javaClass.replaceAll("[.]properties[.]([^.]*)", "_$1");
        javaClass = javaClass.replaceAll("[.]items", "_items");

        int firstCharInName = javaClass.lastIndexOf(".") + 1;
        return basePackage + "." + javaClass.substring(0, firstCharInName) + Character.toUpperCase(javaClass.charAt(firstCharInName)) + javaClass.substring(firstCharInName + 1);
    }

    static String getJavaLangType(JsonArray pathToSchema, JsonObject openApiDoc, String basePackage) {
        JsonValue schema = openApiDoc.get(pathToSchema);
        JsonValue type = schema.get("type");

        if (type != null && type.isString()) {
            return switch (type.getString()) {
                //case "array" -> "List<" + createJavaClassName(pathToSchema.add("items"), openApiDoc, basePackage) + ">";
                case "string" -> "String";
                case "number", "integer" -> "Number";
                case "boolean" -> "Boolean";
                default -> null;
            };
        }
        throw new RuntimeException("Add support for multi-type schemas (especially null)");
    }

    private static boolean isJavaLangType(String javaClassName) {
        return switch (javaClassName) {
            case "String", "Boolean", "Number" -> true;
            default -> false;
        };
    }
}
