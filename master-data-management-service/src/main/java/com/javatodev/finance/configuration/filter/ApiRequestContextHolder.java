package com.javatodev.finance.configuration.filter;

import lombok.extern.slf4j.Slf4j;

/**
 * Thread-local holder for the current API request context.
 * Stores the authenticated user ID extracted from the X-Auth-Id header.
 */
@Slf4j
public final class ApiRequestContextHolder {

    private ApiRequestContextHolder() {
    }

    private static final ThreadLocal<ApiRequestContext> contextHolder = new ThreadLocal<>();

    public static void clearContext() {
        contextHolder.remove();
    }

    public static ApiRequestContext getContext() {
        ApiRequestContext ctx = contextHolder.get();
        if (ctx == null) {
            ctx = new ApiRequestContext();
            contextHolder.set(ctx);
            log.debug("getContext() : new APIRequestContext created..!");
        }
        return ctx;
    }
}
