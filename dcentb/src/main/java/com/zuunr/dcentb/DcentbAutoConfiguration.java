package com.zuunr.dcentb;

import com.zuunr.dcentb.http.HttpController;
import com.zuunr.dcentb.http.SwaggerController;
import com.zuunr.dcentb.rest.controller.Controller;
import com.zuunr.dcentb.rest.controller.RequestHandlerProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;

import java.io.IOException;

@AutoConfiguration
public class DcentbAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public RequestHandlerProvider requestHandlerProvider(
            @Value("${dcentb.openapi.file:classpath:demo.openapi.json}") Resource resource,
            @Value("${dcentb.mongodb.connection:mongodb://admin:adminpassword@localhost:27017/?authSource=admin}") String mongodbConnectionString,
            @Value("${dcentb.mongodb.db:}") String databaseName
    ) throws IOException {
        return new RequestHandlerProvider(resource, mongodbConnectionString, databaseName);
    }

    @Bean
    @ConditionalOnMissingBean
    public Controller dcentbController(RequestHandlerProvider requestHandlerProvider) {
        return new Controller(requestHandlerProvider);
    }

    @Bean
    @ConditionalOnMissingBean
    public HttpController httpController(Controller controller) {
        return new HttpController(controller);
    }

    @Bean
    @ConditionalOnMissingBean
    public SwaggerController swaggerController(
            @Value("${dcentb.openapi.file:classpath:demo.openapi.json}") Resource openapiResource) {
        return new SwaggerController(openapiResource);
    }
}