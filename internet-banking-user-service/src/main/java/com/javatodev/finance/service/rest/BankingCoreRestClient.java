package com.javatodev.finance.service.rest;

import com.javatodev.finance.model.rest.response.UserResponse;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

// fallbackFactory enables Resilience4j circuit breaker fallback for core-banking-service calls
@FeignClient(name = "core-banking-service", fallbackFactory = BankingCoreRestClientFallback.class)
public interface BankingCoreRestClient {

    @GetMapping("/api/v1/user/{identification}")
    UserResponse readUser(@PathVariable("identification") String identification);

}
