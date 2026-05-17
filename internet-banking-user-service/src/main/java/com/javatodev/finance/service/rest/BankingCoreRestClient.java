package com.javatodev.finance.service.rest;

import com.javatodev.finance.model.rest.response.UserResponse;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

// Circuit breaker enabled via fallbackFactory for core-banking-service unavailability
@FeignClient(name = "core-banking-service", fallbackFactory = BankingCoreRestClientFallbackFactory.class)
public interface BankingCoreRestClient {

    @GetMapping("/api/v1/user/{identification}")
    UserResponse readUser(@PathVariable("identification") String identification);

}
