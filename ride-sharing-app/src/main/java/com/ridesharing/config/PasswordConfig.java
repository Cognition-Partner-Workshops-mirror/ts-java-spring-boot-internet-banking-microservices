package com.ridesharing.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Separate configuration for PasswordEncoder bean.
 * Extracted from SecurityConfig to break the circular dependency chain:
 * SecurityConfig → JwtAuthenticationFilter → UserService → PasswordEncoder.
 */
@Configuration
public class PasswordConfig {

    /** BCrypt password encoder for secure password hashing */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
