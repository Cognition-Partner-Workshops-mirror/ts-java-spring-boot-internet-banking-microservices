package com.javatodev.finance.configuration;

import feign.RequestInterceptor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Feign client interceptor that adds the X-Internal-Api-Key header to all outgoing
 * Feign requests, enabling authenticated inter-service communication.
 */
@Configuration
public class FeignConfig {

    @Value("${app.security.internal-api-key}")
    private String internalApiKey;

    @Bean
    public RequestInterceptor requestInterceptor() {
        return template -> template.header("X-Internal-Api-Key", internalApiKey);
    }
}
