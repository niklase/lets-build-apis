package com.zuunr.services;

import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Controller {

    @GetMapping(value = "/hello-world", produces = "application/json")
    public ResponseEntity<String> get() {
        return ResponseEntity.ok(JsonObject.EMPTY.put("hello", "world").asJson());

    }
}
