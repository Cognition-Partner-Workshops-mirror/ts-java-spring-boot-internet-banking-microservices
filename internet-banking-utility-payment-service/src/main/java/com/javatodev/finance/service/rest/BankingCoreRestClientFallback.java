package com.javatodev.finance.service.rest;

import com.javatodev.finance.model.rest.request.UtilityPaymentRequest;
import com.javatodev.finance.model.rest.response.AccountResponse;
import com.javatodev.finance.model.rest.response.UtilityPaymentResponse;

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
    public AccountResponse readAccount(String accountNumber) {
        log.error("Circuit breaker fallback: readAccount failed for account {}", accountNumber, cause);
        throw new RuntimeException("Core banking service unavailable. Please try again later.");
    }

    @Override
    public UtilityPaymentResponse utilityPayment(UtilityPaymentRequest paymentRequest) {
        log.error("Circuit breaker fallback: utilityPayment failed", cause);
        throw new RuntimeException("Core banking service unavailable. Utility payment could not be processed.");
    }
}
