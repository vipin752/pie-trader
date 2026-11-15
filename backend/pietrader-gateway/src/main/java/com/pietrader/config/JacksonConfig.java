package com.pietrader.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Single source of truth for Jackson ObjectMapper.
 * @Primary ensures this bean wins over Spring Boot auto-configured one
 * and is injected into ALL components: AnalyticsConsumer, TradeSignalMapper, etc.
 */
@Configuration
public class JacksonConfig {

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // Java 8 date/time support
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // ✅ Never fail on unknown JSON fields (Python engine may add extra fields)
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        // ✅ Allow 23150.0 / 2.0 (float) into Integer fields safely
        mapper.enable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);

        // ✅ Don't fail on null for primitive types
        mapper.disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);

        // ✅ Empty string → null (not error) for wrapper types
        mapper.enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT);

        return mapper;
    }
}
