package com.javatodev.finance.configuration.filter;

import org.apache.commons.lang.StringUtils;

import java.io.IOException;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * Filter that extracts the X-Auth-Id header set by the gateway and stores it in the request context.
 * Rejects requests with missing or spoofed "SYSTEM USER" auth IDs to prevent header spoofing attacks.
 */
@Slf4j
public class AppAuthUserFilter implements Filter {

    private static final String HTTP_HEADER_AUTH_USER_ID = "X-Auth-Id";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpServletRequest = (HttpServletRequest) request;
        String userAuthId = httpServletRequest.getHeader(HTTP_HEADER_AUTH_USER_ID);
        log.debug("Incoming authenticated request received");

        // Reject requests with missing or spoofed X-Auth-Id header
        if (StringUtils.isEmpty(userAuthId) || "SYSTEM USER".equals(userAuthId)) {
            ((HttpServletResponse) response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        ApiRequestContextHolder.getContext().setAuthId(userAuthId);
        try {
            chain.doFilter(request, response);
        } finally {
            ApiRequestContextHolder.clearContext();
        }
    }
}
