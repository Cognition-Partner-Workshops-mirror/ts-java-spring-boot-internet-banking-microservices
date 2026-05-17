package com.javatodev.finance.configuration;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * In-memory rate limiting filter using Bucket4j token bucket algorithm.
 * Limits each user (identified by JWT subject) to 100 requests per minute.
 * Falls back to IP-based limiting for unauthenticated requests.
 */
@Component
public class RateLimitingFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RateLimitingFilter.class);

    // Thread-safe map of per-user rate limit buckets
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    private static final int REQUESTS_PER_MINUTE = 100;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String key = resolveKey(exchange);
        Bucket bucket = buckets.computeIfAbsent(key, k -> createBucket());

        if (bucket.tryConsume(1)) {
            return chain.filter(exchange);
        }

        // Rate limit exceeded — return 429 Too Many Requests
        log.warn("Rate limit exceeded for key: {}", key);
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        exchange.getResponse().getHeaders().add("X-Rate-Limit-Retry-After-Seconds", "60");
        return exchange.getResponse().setComplete();
    }

    @Override
    public int getOrder() {
        // Run before other filters to reject early
        return -1;
    }

    /**
     * Resolves the rate limiting key from JWT subject or falls back to client IP.
     */
    private String resolveKey(ServerWebExchange exchange) {
        // Try to extract user from principal (JWT subject)
        return exchange.getPrincipal()
            .map(principal -> "user:" + principal.getName())
            .defaultIfEmpty("ip:" + getClientIp(exchange))
            .block();
    }

    private String getClientIp(ServerWebExchange exchange) {
        String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isEmpty()) {
            return forwarded.split(",")[0].trim();
        }
        return exchange.getRequest().getRemoteAddress() != null
            ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
            : "unknown";
    }

    /**
     * Creates a new token bucket with the configured rate limit (100 requests/minute).
     */
    private Bucket createBucket() {
        return Bucket.builder()
            .addLimit(Bandwidth.simple(REQUESTS_PER_MINUTE, Duration.ofMinutes(1)))
            .build();
    }
}
