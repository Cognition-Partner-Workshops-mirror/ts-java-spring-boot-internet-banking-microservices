package com.javatodev.finance.service.rest;

import com.javatodev.finance.model.rest.request.UtilityPaymentRequest;
import com.javatodev.finance.model.rest.response.AccountResponse;
import com.javatodev.finance.model.rest.response.UtilityPaymentResponse;
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
            public AccountResponse readAccount(String accountNumber) {
                log.warn("Fallback: readAccount for {}", accountNumber);
                throw new RuntimeException("Core Banking Service is currently unavailable.", cause);
            }

            @Override
            public UtilityPaymentResponse utilityPayment(UtilityPaymentRequest paymentRequest) {
                log.warn("Fallback: utilityPayment for {}", paymentRequest);
                // Return a fallback response indicating the payment is unavailable
                return UtilityPaymentResponse.builder()
                    .message("Utility payment is currently unavailable. Please try again later.")
                    .transactionId(null)
                    .build();
            }
        };
    }
}
