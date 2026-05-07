package com.javatodev.finance.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.javatodev.finance.exception.ErrorResponse;
import com.javatodev.finance.exception.SimpleBankingGlobalException;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.io.InputStream;

import feign.Logger;
import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class CustomFeignClientConfiguration {

    @Bean
    Logger.Level feignLoggerLevel() {
        return Logger.Level.FULL;
    }

    @Bean
    public ErrorDecoder errorDecoder() {
        return new CustomFeignErrorDecoder();
    }

    private static class CustomFeignErrorDecoder implements ErrorDecoder {

        private final ErrorDecoder defaultDecoder = new Default();
        private final ObjectMapper objectMapper = new ObjectMapper();

        @Override
        public Exception decode(String methodKey, Response response) {
            try {
                if (response.body() != null) {
                    try (InputStream bodyIs = response.body().asInputStream()) {
                        ErrorResponse errorResponse = objectMapper.readValue(bodyIs, ErrorResponse.class);
                        if (errorResponse.getCode() != null) {
                            return new SimpleBankingGlobalException(errorResponse.getCode(), errorResponse.getMessage());
                        }
                    }
                }
            } catch (IOException e) {
                log.warn("Failed to decode Feign error response", e);
            }
            return defaultDecoder.decode(methodKey, response);
        }
    }
}
