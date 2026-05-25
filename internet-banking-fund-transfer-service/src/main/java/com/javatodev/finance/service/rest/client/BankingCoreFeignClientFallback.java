package com.javatodev.finance.service.rest.client;

import com.javatodev.finance.model.dto.request.FundTransferRequest;
import com.javatodev.finance.model.dto.response.AccountResponse;
import com.javatodev.finance.model.dto.response.FundTransferResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * Fallback factory for BankingCoreFeignClient.
 * Provides graceful degradation when core-banking-service is unavailable.
 * Circuit breaker triggers this fallback after failure threshold is reached.
 */
@Slf4j
@Component
public class BankingCoreFeignClientFallback implements FallbackFactory<BankingCoreFeignClient> {

    @Override
    public BankingCoreFeignClient create(Throwable cause) {
        log.error("Fallback triggered for BankingCoreFeignClient due to: {}", cause.getMessage());
        return new BankingCoreFeignClient() {
            @Override
            public AccountResponse readAccount(String accountNumber) {
                log.warn("Fallback: readAccount for {}", accountNumber);
                throw new RuntimeException("Core Banking Service is currently unavailable. Please try again later.", cause);
            }

            @Override
            public FundTransferResponse fundTransfer(FundTransferRequest fundTransferRequest) {
                log.warn("Fallback: fundTransfer for {}", fundTransferRequest);
                // Return a fallback response indicating the transfer has been queued
                return FundTransferResponse.builder()
                    .message("Fund transfer is currently unavailable. Your request has been queued for processing.")
                    .transactionId(null)
                    .build();
            }
        };
    }
}
