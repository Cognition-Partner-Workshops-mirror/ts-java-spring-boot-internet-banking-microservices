package com.javatodev.finance.service.rest.client;

import com.javatodev.finance.model.dto.request.FundTransferRequest;
import com.javatodev.finance.model.dto.response.AccountResponse;
import com.javatodev.finance.model.dto.response.FundTransferResponse;
import com.javatodev.finance.exception.ServiceException;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class BankingCoreFeignClientFallback implements BankingCoreFeignClient {

    @Override
    public AccountResponse readAccount(String accountNumber) {
        log.warn("Circuit breaker fallback: readAccount for account ending {}", accountNumber.substring(Math.max(0, accountNumber.length() - 4)));
        throw new ServiceException("Core banking service is unavailable", HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Override
    public FundTransferResponse fundTransfer(FundTransferRequest fundTransferRequest) {
        log.warn("Circuit breaker fallback: fundTransfer");
        throw new ServiceException("Core banking service is unavailable", HttpStatus.SERVICE_UNAVAILABLE);
    }
}
