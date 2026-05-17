package com.javatodev.finance.service.rest.client;

import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * Fallback factory for BankingCoreFeignClient circuit breaker.
 * Creates fallback instances that handle core-banking-service unavailability.
 */
@Component
public class BankingCoreFeignClientFallbackFactory implements FallbackFactory<BankingCoreFeignClient> {

    @Override
    public BankingCoreFeignClient create(Throwable cause) {
        return new BankingCoreFeignClientFallback(cause);
    }
}
