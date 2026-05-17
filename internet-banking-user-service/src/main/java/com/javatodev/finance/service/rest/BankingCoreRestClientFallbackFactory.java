package com.javatodev.finance.service.rest;

import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * Fallback factory for BankingCoreRestClient circuit breaker.
 * Creates fallback instances that handle core-banking-service unavailability.
 */
@Component
public class BankingCoreRestClientFallbackFactory implements FallbackFactory<BankingCoreRestClient> {

    @Override
    public BankingCoreRestClient create(Throwable cause) {
        return new BankingCoreRestClientFallback(cause);
    }
}
