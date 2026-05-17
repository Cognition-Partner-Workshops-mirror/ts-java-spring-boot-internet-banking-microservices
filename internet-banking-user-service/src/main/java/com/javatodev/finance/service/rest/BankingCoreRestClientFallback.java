package com.javatodev.finance.service.rest;

import com.javatodev.finance.model.rest.response.UserResponse;

import lombok.extern.slf4j.Slf4j;

/**
 * Circuit breaker fallback for BankingCoreRestClient.
 * Returns error responses when core-banking-service is unavailable.
 */
@Slf4j
public class BankingCoreRestClientFallback implements BankingCoreRestClient {

    private final Throwable cause;

    public BankingCoreRestClientFallback(Throwable cause) {
        this.cause = cause;
    }

    @Override
    public UserResponse readUser(String identification) {
        log.error("Circuit breaker fallback: readUser failed for identification {}", identification, cause);
        throw new RuntimeException("Core banking service unavailable. Please try again later.");
    }
}
