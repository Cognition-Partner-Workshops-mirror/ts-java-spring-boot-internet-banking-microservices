package com.javatodev.finance.service.rest;

import com.javatodev.finance.model.rest.response.UserResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * Fallback factory for BankingCoreRestClient.
 * Provides graceful degradation when core-banking-service is unavailable.
 * Circuit breaker triggers this fallback after failure threshold is reached.
 */
@Slf4j
@Component
public class BankingCoreRestClientFallback implements FallbackFactory<BankingCoreRestClient> {

    @Override
    public BankingCoreRestClient create(Throwable cause) {
        log.error("Fallback triggered for BankingCoreRestClient due to: {}", cause.getMessage());
        return new BankingCoreRestClient() {
            @Override
            public UserResponse readUser(String identification) {
                log.warn("Fallback: readUser for {}", identification);
                throw new RuntimeException("Core Banking Service is currently unavailable. Cannot verify user at this time.", cause);
            }
        };
    }
}
