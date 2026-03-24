package com.sinosig.aip.app.config;

import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class JacksonConfig {

    @Value("${aip.http.jackson.max-string-length:60000000}")
    private int maxStringLength;

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        JsonFactory jsonFactory = JsonFactory.builder()
                .streamReadConstraints(StreamReadConstraints.builder()
                        .maxStringLength(maxStringLength)
                        .build())
                .build();
        return new ObjectMapper(jsonFactory)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .findAndRegisterModules();
    }
}

