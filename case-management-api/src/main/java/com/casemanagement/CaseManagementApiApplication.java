package com.casemanagement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main entry point for the Case Management API application.
 * This Spring Boot application provides REST endpoints for managing case data
 * with Oracle database backend and comprehensive request logging for reporting.
 */
@SpringBootApplication
public class CaseManagementApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(CaseManagementApiApplication.class, args);
    }
}
