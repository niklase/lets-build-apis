package com.zuunr.example;

import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class EndToEndTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    public void shouldReturnExpectedData_whenEndpointIsCalled() {
        String url = "http://localhost:" + port + "/test";

        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.add("content-type", "application/json");
        httpHeaders.add("accept", "application/json");
        httpHeaders.add("x-api-key", "apisecret");

        JsonValue body = JsonObject.EMPTY
                .put("hello", "you")
                .put("hidden_in_response", "I do not show!")
                .jsonValue();

        HttpEntity<JsonValue> httpEntity = new HttpEntity<>(body, httpHeaders);
        ResponseEntity<JsonValue> response = restTemplate.exchange(URI.create(url), HttpMethod.POST, httpEntity, ParameterizedTypeReference.forType(JsonValue.class));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).isEqualTo(JsonObject.EMPTY.put("hello", "you").jsonValue());
    }
}

