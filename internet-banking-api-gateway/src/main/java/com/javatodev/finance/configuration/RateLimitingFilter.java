package com.javatodev.finance.configuration;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * In-memory rate limiting filter using Bucket4j token bucket algorithm.
 * Limits each user (identified by JWT subject) to 100 requests per minute.
 * Falls back to IP-based limiting for unauthenticated requests.
 * Uses Caffeine cache with TTL and max size to prevent unbounded memory growth.
 */
@Component
public class RateLimitingFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RateLimitingFilter.class);

    // Bounded cache with TTL eviction to prevent memory leaks from unbounded bucket storage
    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
        .expireAfterAccess(Duration.ofMinutes(5))
        .maximumSize(100_000)
        .build();

    private static final int REQUESTS_PER_MINUTE = 100;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Fully reactive key resolution — no block() calls to avoid IllegalStateException on Netty threads
        return resolveKey(exchange).flatMap(key -> {
            Bucket bucket = buckets.get(key, k -> createBucket());

            if (bucket.tryConsume(1)) {
                return chain.filter(exchange);
            }

            // Rate limit exceeded — return 429 Too Many Requests
            log.warn("Rate limit exceeded for key: {}", key);
            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            exchange.getResponse().getHeaders().add("X-Rate-Limit-Retry-After-Seconds", "60");
            return exchange.getResponse().setComplete();
        });
    }

    @Override
    public int getOrder() {
        // Run before other filters to reject early
        return -1;
    }

    /**
     * Resolves the rate limiting key reactively from JWT subject or falls back to client IP.
     * Returns Mono<String> to avoid blocking on Netty event loop threads.
     */
    private Mono<String> resolveKey(ServerWebExchange exchange) {
        return exchange.getPrincipal()
            .map(principal -> "user:" + principal.getName())
            .switchIfEmpty(Mono.fromSupplier(() -> "ip:" + getClientIp(exchange)));
    }

    private String getClientIp(ServerWebExchange exchange) {
        // Use remote address directly — do not trust X-Forwarded-For to prevent spoofing
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
