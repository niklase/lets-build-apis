/*
 * Copyright (c) Zuunr AB. All rights reserved.
 */
package com.zuunr.api;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.zuunr.json.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
//import org.springframework.scheduling.annotation.EnableAsync;

/**
 * @author Niklas Eldberger
 */
//@EnableAsync
@ConfigurationPropertiesScan
@SpringBootApplication(scanBasePackages = {"com.zuunr.api.design"})
public class Application {

    public static void main(String... args) {
        SpringApplication.run(Application.class, args);
    }

    @Bean
    public Module jsonValueDeserializer() {
        SimpleModule module = new SimpleModule();
        module.addDeserializer(JsonValue.class, new JsonValueDeserializer());
        return module;
    }

    @Bean
    ObjectMapper objectMapper() {

        ObjectMapper mapper = new ObjectMapper();
        SimpleModule module = new SimpleModule();

        module.addDeserializer(JsonValue.class, new JsonValueDeserializer());
        module.addSerializer(JsonValue.class, new JsonValueSerializer());

        mapper.registerModule(module);
        mapper.enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        mapper.enable(DeserializationFeature.USE_LONG_FOR_INTS);
        mapper.setNodeFactory(JsonNodeFactory.withExactBigDecimals(true));
        return mapper;
    }
}