package com.javatodev.finance.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

import reactor.core.publisher.Mono;

/**
 * Gateway global filter that forwards authentication context headers to downstream services.
 * Adds X-Auth-Id (user principal), X-Internal-Api-Key (inter-service auth),
 * and X-Auth-Roles (Keycloak realm roles) to all proxied requests.
 */
@Configuration
public class GatewayConfiguration {

    private static final String HTTP_HEADER_AUTH_USER_ID = "X-Auth-Id";
    private static final String UNAUTHORIZED_USER_NAME = "SYSTEM USER";

    // Internal API key injected from config server for inter-service authentication
    @Value("${app.security.internal-api-key}")
    private String internalApiKey;

    @Bean
    public GlobalFilter customGlobalFilter() {
        // Fully reactive pipeline — no block() calls to avoid IllegalStateException on Netty threads
        return (exchange, chain) -> exchange.getPrincipal()
            .cast(JwtAuthenticationToken.class)
            .map(jwtAuth -> {
                String principalName = jwtAuth.getName();
                // Extract roles directly from the already-resolved JWT token (no second block() call)
                String roles = extractRolesFromToken(jwtAuth);
                exchange.getRequest().mutate()
                    .header(HTTP_HEADER_AUTH_USER_ID, principalName)
                    .header("X-Internal-Api-Key", internalApiKey)
                    .header("X-Auth-Roles", roles)
                    .build();
                return exchange;
            })
            .defaultIfEmpty(exchange)
            .flatMap(chain::filter);
    }

    /**
     * Extracts realm roles from a Keycloak JWT token.
     * Accepts JwtAuthenticationToken directly to avoid needing a second block() call.
     * Returns comma-separated role names for forwarding to downstream services.
     */
    @SuppressWarnings("unchecked")
    private String extractRolesFromToken(JwtAuthenticationToken jwtAuth) {
        Map<String, Object> realmAccess = jwtAuth.getToken().getClaimAsMap("realm_access");
        if (realmAccess != null) {
            Object rolesObj = realmAccess.get("roles");
            if (rolesObj instanceof Collection<?> roles) {
                return roles.stream()
                    .map(Object::toString)
                    .collect(Collectors.joining(","));
            }
        }
        return "";
    }

}
