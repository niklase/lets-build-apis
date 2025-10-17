package com.zuunr.api.design.controller;

import com.zuunr.json.JsonValue;
import com.zuunr.json.JsonValueFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.server.reactive.ServerHttpRequest;
import reactor.core.publisher.Mono;

public class RequestBodyUtils {

    public static Mono<JsonValue> getBodyAsJsonValue(ServerHttpRequest request) {
        return DataBufferUtils.join(request.getBody())
                .map(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    JsonValue result = JsonValueFactory.create(dataBuffer.asInputStream());
                    DataBufferUtils.release(dataBuffer); // Release the buffer back to the pool
                    return result;
                });
    }
}