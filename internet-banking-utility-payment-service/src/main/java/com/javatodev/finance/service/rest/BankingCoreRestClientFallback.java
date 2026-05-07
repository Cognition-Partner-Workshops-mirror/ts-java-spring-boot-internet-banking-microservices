package com.javatodev.finance.service.rest;

import com.javatodev.finance.exception.ServiceException;
import com.javatodev.finance.model.rest.request.UtilityPaymentRequest;
import com.javatodev.finance.model.rest.response.AccountResponse;
import com.javatodev.finance.model.rest.response.UtilityPaymentResponse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class BankingCoreRestClientFallback implements BankingCoreRestClient {

    @Override
    public AccountResponse readAccount(String accountNumber) {
        log.warn("Circuit breaker fallback: readAccount for account ending {}", accountNumber.substring(Math.max(0, accountNumber.length() - 4)));
        throw new ServiceException("Core banking service is unavailable", HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Override
    public UtilityPaymentResponse utilityPayment(UtilityPaymentRequest paymentRequest) {
        log.warn("Circuit breaker fallback: utilityPayment");
        throw new ServiceException("Core banking service is unavailable", HttpStatus.SERVICE_UNAVAILABLE);
    }
}
