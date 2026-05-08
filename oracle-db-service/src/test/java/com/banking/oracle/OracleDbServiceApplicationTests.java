package com.banking.oracle;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integration test to verify that the Spring Boot application context
 * loads successfully. Uses the 'test' profile which configures H2
 * in-memory database instead of Oracle for testing.
 */
@SpringBootTest
@ActiveProfiles("test")
class OracleDbServiceApplicationTests {

    /**
     * Verifies that the Spring application context loads without errors.
     * This catches configuration issues, missing beans, and circular dependencies.
     */
    @Test
    void contextLoads() {
        /* Context load test - passes if application context initializes successfully */
    }
}
