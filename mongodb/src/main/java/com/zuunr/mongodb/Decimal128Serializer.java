/*
 * Copyright 2019 Zuunr AB
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
package com.zuunr.mongodb;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.bson.types.Decimal128;

import java.io.IOException;
import java.math.BigDecimal;

/**
 * <p>The Decimal128Serializer is responsible for converting
 * {@link Decimal128} objects to {@link BigDecimal} objects
 * when utilizing the jackson framework.</p>
 *
 * @author Mikael Ahlberg
 */
public class Decimal128Serializer extends JsonSerializer<Decimal128> {

    @Override
    public void serialize(Decimal128 value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeNumber(value.bigDecimalValue());
    }
}
