package com.javatodev.finance.service.rest;

import com.javatodev.finance.model.rest.response.UserResponse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class BankingCoreRestClientFallback implements BankingCoreRestClient {

    @Override
    public UserResponse readUser(String identification) {
        log.warn("Circuit breaker fallback: readUser");
        throw new RuntimeException("Core banking service is unavailable");
    }
}
