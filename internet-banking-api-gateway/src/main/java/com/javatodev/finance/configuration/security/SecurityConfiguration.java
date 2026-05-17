package com.javatodev.finance.configuration.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.csrf.CookieServerCsrfTokenRepository;
import org.springframework.security.web.server.csrf.CsrfToken;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.server.WebFilter;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Gateway security configuration with CSRF protection, CORS, RBAC role-based access control,
 * and JWT authentication via Keycloak. Extracts realm roles from JWT for authorization.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfiguration {

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
    private String jwkUri;

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {

        ServerHttpSecurity httpSecurity = http
            .authorizeExchange(exchanges -> {

                //ALLOW USER REGISTRATION API ENDPOINT
                exchanges.pathMatchers("/user/api/v1/bank-users/register").permitAll();

                //ALLOW ACTUATOR ENDPOINTS
                exchanges.pathMatchers("/actuator/**").permitAll()
                    .pathMatchers("/user/actuator/**").permitAll()
                    .pathMatchers("/fund-transfer/actuator/**").permitAll()
                    .pathMatchers("/banking-core/actuator/**").permitAll()
                    .pathMatchers("/utility-payment/actuator/**").permitAll()

                    // RBAC rules — admin-only endpoints
                    .pathMatchers(HttpMethod.PATCH, "/user/api/v1/bank-users/update/**").hasRole("ADMIN")
                    .pathMatchers(HttpMethod.GET, "/user/api/v1/bank-users").hasRole("ADMIN")
                    .pathMatchers(HttpMethod.GET, "/banking-core/api/v1/user/**").hasRole("ADMIN")

                    // RBAC rules — user and admin endpoints
                    .pathMatchers(HttpMethod.POST, "/fund-transfer/api/v1/transfer").hasAnyRole("USER", "ADMIN")
                    .pathMatchers(HttpMethod.POST, "/utility-payment/api/v1/utility-payment").hasAnyRole("USER", "ADMIN")
                    .pathMatchers(HttpMethod.GET, "/fund-transfer/api/v1/transfer").hasAnyRole("USER", "ADMIN")

                    .anyExchange().authenticated();
            });

        // CSRF protection with cookie-based token repository for browser clients
        // Bearer token API clients are exempt (they are inherently CSRF-safe)
        httpSecurity.csrf(csrf -> csrf
            .csrfTokenRepository(CookieServerCsrfTokenRepository.withHttpOnlyFalse())
            .requireCsrfProtectionMatcher(exchange -> {
                String method = exchange.getRequest().getMethod().name();
                // Only require CSRF for state-changing methods
                if ("GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method)) {
                    return ServerWebExchangeMatcher.MatchResult.notMatch();
                }
                // Skip CSRF for API clients using Bearer tokens (not vulnerable to CSRF)
                String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    return ServerWebExchangeMatcher.MatchResult.notMatch();
                }
                return ServerWebExchangeMatcher.MatchResult.match();
            })
        );

        // CORS configuration for external client origins
        httpSecurity.cors(cors -> cors.configurationSource(corsConfigurationSource()));

        // JWT authentication with Keycloak role extraction
        httpSecurity.oauth2ResourceServer(oAuth2ResourceServer ->
            oAuth2ResourceServer.jwt(jwt ->
                jwt.jwkSetUri(jwkUri).jwtAuthenticationConverter(jwtAuthenticationConverter())
            )
        );

        return httpSecurity.build();

    }

    /**
     * Subscribes to the CSRF token on every request so the cookie is always set.
     * Required for JS frontends to read the CSRF token from the cookie.
     */
    @Bean
    public WebFilter csrfTokenSubscriber() {
        return (exchange, chain) -> {
            Mono<CsrfToken> csrfToken = exchange.getAttributeOrDefault(CsrfToken.class.getName(), Mono.empty());
            return csrfToken.doOnSuccess(token -> {}).then(chain.filter(exchange));
        };
    }

    /**
     * Maps Keycloak realm_access.roles from the JWT to Spring Security granted authorities.
     * Enables role-based access control (RBAC) at the gateway level.
     */
    @SuppressWarnings("unchecked")
    @Bean
    public ReactiveJwtAuthenticationConverter jwtAuthenticationConverter() {
        ReactiveJwtAuthenticationConverter converter = new ReactiveJwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<GrantedAuthority> authorities = new ArrayList<>();
            Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
            if (realmAccess != null) {
                Object rolesObj = realmAccess.get("roles");
                if (rolesObj instanceof List<?> roles) {
                    roles.forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
                }
            }
            return Flux.fromIterable(authorities);
        });
        return converter;
    }

    /**
     * CORS configuration allowing external client origins to access the gateway.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Allow all origins for now — restrict to specific frontend origins in production
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

}
