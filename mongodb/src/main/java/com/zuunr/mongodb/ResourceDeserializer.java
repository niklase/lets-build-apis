package com.zuunr.mongodb;
/*
 * Copyright 2018 Zuunr AB
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.zuunr.json.JsonObject;
import jakarta.annotation.PostConstruct;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Component;

/**
 * <p>The ResourceDeserializer is responsible for converting Objects
 * to a defined resource class.</p>
 *
 * @author Mikael Ahlberg
 */
@Component
public class ResourceDeserializer {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public ResourceDeserializer init() {
        objectMapper.registerModule(new SimpleModule()
                .addSerializer(Decimal128.class, new Decimal128Serializer())
                .addSerializer(ObjectId.class, new com.zuunr.mongodb.ObjectIdSerializer())
        );

        return this;
    }

    /**
     * <p>Deserializes the provided resource to the given resource class.</p>
     *
     * <p>If an error occurs a {@link DeserializationException} will be thrown.</p>
     *
     * @param resource      is the resource to convert
     * @param resourceClass is the class to convert to
     * @return a new object of resourceClass type
     */
    public <T> T deserialize(Object resource, Class<T> resourceClass) {
        try {
            return objectMapper.convertValue(resource, resourceClass);
        } catch (Exception e) {
            throw new DeserializationException("Could not deserialize resource to given type", e);
        }
    }

    /**
     * <p>Convenience method for deserializing {@link JsonObject}.</p>
     *
     * <p>If an error occurs a {@link DeserializationException} will be thrown.</p>
     *
     * @param resource      is the resource to convert
     * @param resourceClass is the class to convert to
     * @return a new object of resourceClass type
     */
    public <T> T deserializeJsonObject(JsonObject resource, Class<T> resourceClass) {
        return deserialize(resource.asMapsAndLists(), resourceClass);
    }
}
