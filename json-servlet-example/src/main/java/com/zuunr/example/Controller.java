package com.zuunr.example;

import com.zuunr.json.JsonValue;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Controller {

    @PostMapping(value = "/test", consumes = "application/json", produces = "application/json")

    public ResponseEntity<JsonValue> post(@RequestBody JsonValue body, HttpServletResponse response) {
        return ResponseEntity.ok(body);

    }

    @PostMapping(value = "/test/{id}", consumes = "application/json", produces = "application/json")
    public ResponseEntity<JsonValue> postWithId(@RequestBody JsonValue body, HttpServletResponse response) {
        return ResponseEntity.ok(body);
    }
}
