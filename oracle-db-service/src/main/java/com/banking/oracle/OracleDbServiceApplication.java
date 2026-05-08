package com.banking.oracle;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main entry point for the Oracle Database Service Spring Boot application.
 * This service provides REST APIs backed by an Oracle Database,
 * demonstrating Spring Data JPA integration with Oracle DB.
 */
@SpringBootApplication
public class OracleDbServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OracleDbServiceApplication.class, args);
    }
}
