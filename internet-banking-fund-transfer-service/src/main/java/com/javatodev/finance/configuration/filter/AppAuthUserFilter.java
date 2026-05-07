package com.javatodev.finance.configuration.filter;

import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AppAuthUserFilter implements Filter {

    private static final String HTTP_HEADER_AUTH_USER_ID = "X-Auth-Id";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpServletRequest = (HttpServletRequest) request;
        String userAuthId = httpServletRequest.getHeader(HTTP_HEADER_AUTH_USER_ID);

        if (userAuthId == null || userAuthId.isBlank()) {
            log.warn("Request rejected: missing X-Auth-Id header for {} {}", httpServletRequest.getMethod(), httpServletRequest.getRequestURI());
            HttpServletResponse httpResponse = (HttpServletResponse) response;
            httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            httpResponse.setContentType("application/json");
            httpResponse.getWriter().write("{\"code\":\"UNAUTHORIZED\",\"message\":\"Missing X-Auth-Id header\"}");
            return;
        }

        log.info("Incoming Request From authenticated user");
        ApiRequestContextHolder.getContext().setAuthId(userAuthId);
        try {
            chain.doFilter(request, response);
        } finally {
            ApiRequestContextHolder.clearContext();
        }
    }
}
