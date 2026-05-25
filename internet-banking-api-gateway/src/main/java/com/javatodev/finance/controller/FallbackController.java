package com.javatodev.finance.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import java.util.Map;

/**
 * Gateway fallback controller for circuit breaker integration.
 * Returns a standard SERVICE_UNAVAILABLE response when downstream services are unreachable
 * and the circuit breaker trips via the gateway route filter configuration.
 */
@RestController
public class FallbackController {

    @GetMapping("/fallback")
    public Mono<Map<String, String>> fallbackGet() {
        return Mono.just(Map.of(
            "status", "SERVICE_UNAVAILABLE",
            "message", "The requested service is temporarily unavailable. Please try again later."
        ));
    }

    @PostMapping("/fallback")
    public Mono<Map<String, String>> fallbackPost() {
        return Mono.just(Map.of(
            "status", "SERVICE_UNAVAILABLE",
            "message", "The requested service is temporarily unavailable. Please try again later."
        ));
    }
}
