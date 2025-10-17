package com.zuunr.api.objectgen;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ZipFileCreator {

    public static void createZipFromMap(Map<String, String> javaClasses, String zipFilePath) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(zipFilePath);
             ZipOutputStream zipOut = new ZipOutputStream(fos)) {

            for (Map.Entry<String, String> entry : javaClasses.entrySet()) {
                String className = entry.getKey().replace('.', '/') + ".java"; // Class file name
                String classCode = entry.getValue();         // Java class code

                // Create a ZipEntry for the class file
                ZipEntry zipEntry = new ZipEntry(className);
                zipOut.putNextEntry(zipEntry);

                // Write the class code as bytes into the zip
                byte[] classBytes = classCode.getBytes();
                zipOut.write(classBytes, 0, classBytes.length);

                zipOut.closeEntry();
            }
        }
    }

    public static void main(String[] args) {
        // Sample map with Java class names and code
        Map<String, String> javaClasses = Map.of(
                "com.example.HelloWorld", "public class HelloWorld { public static void main(String[] args) { System.out.println(\"Hello, World!\"); } }",
                "MyClass", "public class MyClass { public void sayHello() { System.out.println(\"Hello from MyClass\"); } }"
        );

        String zipFilePath = "classes.zip";

        try {
            createZipFromMap(javaClasses, zipFilePath);
            System.out.println("Classes have been written to the zip file: " + zipFilePath);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}