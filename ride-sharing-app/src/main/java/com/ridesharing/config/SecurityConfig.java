package com.ridesharing.config;

import com.ridesharing.security.JwtAuthenticationEntryPoint;
import com.ridesharing.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security configuration for the ride-sharing application.
 * Configures JWT-based stateless authentication with role-based access control.
 * Public endpoints: auth, swagger, actuator, h2-console.
 * Protected endpoints: all API routes require authentication.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.jwtAuthenticationEntryPoint = jwtAuthenticationEntryPoint;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // Disable CSRF since we use JWT tokens (stateless)
                .csrf(AbstractHttpConfigurer::disable)

                // Allow H2 console frames
                .headers(headers -> headers
                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin))

                // Configure exception handling for unauthorized access
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint))

                // Use stateless sessions (no server-side session storage)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // Define URL authorization rules
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints - authentication
                        .requestMatchers("/api/auth/**").permitAll()
                        // Public endpoints - API documentation
                        .requestMatchers("/swagger-ui/**", "/api-docs/**", "/swagger-ui.html").permitAll()
                        // Public endpoints - H2 console (development only)
                        .requestMatchers("/h2-console/**").permitAll()
                        // Public endpoints - Actuator health check
                        .requestMatchers("/actuator/health").permitAll()
                        // Fare estimate is public for non-logged-in users to see prices
                        .requestMatchers(HttpMethod.POST, "/api/rides/estimate").permitAll()
                        // All other requests require authentication
                        .anyRequest().authenticated())

                // Add JWT filter before Spring Security's default auth filter
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /** Authentication manager bean for login processing */
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }
}
