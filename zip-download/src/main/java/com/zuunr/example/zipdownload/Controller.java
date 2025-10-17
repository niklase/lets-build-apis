package com.zuunr.example.zipdownload;

import com.fasterxml.jackson.databind.util.ISO8601Utils;
import com.zuunr.api.objectgen.JavaModelCodeGenerator;
import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@RestController
public class Controller {


    @PostMapping(value = "/create-java-classes", consumes = "application/json")
    public void getZip(@RequestBody JsonValue body, HttpServletResponse response) throws IOException {


        String fileName = body.get("fileName", "java_classes_for_openapi_doc_"+ ISO8601Utils.format(new Date())+".zip").getString();

        JsonObject classes = JavaModelCodeGenerator.createJavaClasses(
                body.get("openApiDoc", JsonObject.EMPTY).getJsonObject(),
                body.get("targetBasePackage", "com.example").getString());
        response.setHeader(HttpHeaders.CONTENT_TYPE, "application/zip");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\""+fileName+"\"");

        // Write the zip to a ByteArrayOutputStream
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        ZipFileCreator.createZipFromMap(convertJsonObjectToMap(classes), byteArrayOutputStream);

        // Cache the zip as a byte array
        byte[] bytes = byteArrayOutputStream.toByteArray();

        // Write the response headers
        response.setHeader(HttpHeaders.CONTENT_TYPE, "application/zip");
        response.getOutputStream().write(bytes);
    }

    private Map<String, String> convertJsonObjectToMap(JsonObject jsonObject) {
        return jsonObject.asMapsAndLists().entrySet()
                .stream()
                .collect(Collectors.toMap(
                        entry -> (String) entry.getKey(),
                        entry -> (String) entry.getValue()
                ));
    }

    @GetMapping("/zip-file")
    public void getZip(HttpServletResponse response) throws IOException {
        JsonObject classes = JsonObject.EMPTY.put("com.example.MyClass", "package com.example;\n\npublic class MyClass {\n\n}");
        response.setHeader(HttpHeaders.CONTENT_TYPE, "application/zip");
        ZipFileCreator.createZipFromMap(convertJsonObjectToMap(classes), response.getOutputStream());
    }
}
