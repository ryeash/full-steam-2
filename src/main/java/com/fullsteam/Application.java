package com.fullsteam;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.runtime.Micronaut;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;

public class Application {
    public static void main(String[] args) {
        Micronaut.run(Application.class, args);
    }

    @Singleton
    static class ObjectMapperBeanEventListener implements BeanCreatedEventListener<ObjectMapper> {

        @Override
        public ObjectMapper onCreated(BeanCreatedEvent<ObjectMapper> event) {
            return event.getBean()
                    .setDefaultMergeable(true)
                    .configure(JsonGenerator.Feature.IGNORE_UNKNOWN, true)
                    .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false)
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                    .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
                    .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                    .setSerializationInclusion(JsonInclude.Include.ALWAYS)
                    .setDefaultPropertyInclusion(JsonInclude.Include.ALWAYS)
                    .registerModule(new SimpleModule()
                            .addSerializer(Double.class, new SerializerDouble())
                            .addSerializer(double.class, new SerializerDouble())
                            .addSerializer(BigDecimal.class, new SerializerBigDecimal())
                    );
        }
    }

    public static class SerializerBigDecimal extends JsonSerializer<BigDecimal> {
        @Override
        public void serialize(BigDecimal value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            if (value != null) {
                gen.writeNumber(value.setScale(1, RoundingMode.FLOOR));
            } else {
                gen.writeNull();
            }
        }
    }

    public static class SerializerDouble extends JsonSerializer<Double> {
        @Override
        public void serialize(Double value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            if (value != null) {
                if (value.isInfinite()) {
                    gen.writeNumber(999999); // a large number
                } else {
                    BigDecimal bd = BigDecimal.valueOf(value);
                    gen.writeNumber(bd.setScale(1, RoundingMode.FLOOR));
                }
            } else {
                gen.writeNull();
            }
        }
    }
}
