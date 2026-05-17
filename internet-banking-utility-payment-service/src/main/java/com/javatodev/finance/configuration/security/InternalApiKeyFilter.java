package com.javatodev.finance.configuration.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Filter that validates the X-Internal-Api-Key header on incoming requests
 * to ensure only authorized internal services (via the gateway or Feign) can call this service.
 */
public class InternalApiKeyFilter extends OncePerRequestFilter {

    private final String expectedApiKey;

    public InternalApiKeyFilter(String expectedApiKey) {
        this.expectedApiKey = expectedApiKey;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String apiKey = request.getHeader("X-Internal-Api-Key");
        if (expectedApiKey.equals(apiKey)) {
            // Valid internal API key — authenticate the request as an internal service call
            SecurityContextHolder.getContext().setAuthentication(
                new PreAuthenticatedAuthenticationToken("internal-service", null, List.of(new SimpleGrantedAuthority("ROLE_INTERNAL")))
            );
            filterChain.doFilter(request, response);
        } else {
            // Missing or invalid API key — reject with 401 Unauthorized
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"error\": \"Unauthorized\"}");
        }
    }
}
