package com.javatodev.finance.service.rest.client;

import com.javatodev.finance.model.dto.request.FundTransferRequest;
import com.javatodev.finance.model.dto.response.AccountResponse;
import com.javatodev.finance.model.dto.response.FundTransferResponse;

import lombok.extern.slf4j.Slf4j;

/**
 * Circuit breaker fallback for BankingCoreFeignClient.
 * Returns error responses when core-banking-service is unavailable.
 */
@Slf4j
public class BankingCoreFeignClientFallback implements BankingCoreFeignClient {

    private final Throwable cause;

    public BankingCoreFeignClientFallback(Throwable cause) {
        this.cause = cause;
    }

    @Override
    public AccountResponse readAccount(String accountNumber) {
        log.error("Circuit breaker fallback: readAccount failed for account {}", accountNumber, cause);
        throw new RuntimeException("Core banking service unavailable. Please try again later.");
    }

    @Override
    public FundTransferResponse fundTransfer(FundTransferRequest fundTransferRequest) {
        log.error("Circuit breaker fallback: fundTransfer failed", cause);
        throw new RuntimeException("Core banking service unavailable. Fund transfer could not be processed.");
    }
}
