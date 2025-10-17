package com.zuunr.example.zipdownload;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ZipFileCreator {

    public static void createZipFromMap(Map<String, String> javaClasses, OutputStream outputStream) throws IOException {

        try (ZipOutputStream zipOut = new ZipOutputStream(outputStream)) {

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
                outputStream.close();
            }
        }
    }
}
