package com.banking.oracle.controller;

import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Health check controller to verify Oracle Database connectivity.
 * Provides an endpoint to test that the application can reach
 * the Oracle Database and execute a simple query.
 */
@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Health", description = "Database health check endpoints")
public class HealthCheckController {

    /* DataSource injected to test Oracle DB connectivity */
    private final DataSource dataSource;

    /**
     * GET /api/health/db - Tests Oracle Database connectivity.
     * Executes a simple "SELECT 1 FROM DUAL" query (Oracle-specific)
     * to verify the database connection is working.
     *
     * @return ResponseEntity with connection status and database metadata
     */
    @GetMapping("/db")
    @Operation(summary = "Check database connectivity",
               description = "Tests Oracle Database connection by executing a simple query")
    public ResponseEntity<Map<String, Object>> checkDatabaseHealth() {
        Map<String, Object> healthStatus = new HashMap<>();

        try (var connection = dataSource.getConnection()) {
            /* Execute Oracle-specific dual table query to verify connectivity */
            var statement = connection.createStatement();
            var resultSet = statement.executeQuery("SELECT 1 FROM DUAL");

            if (resultSet.next()) {
                healthStatus.put("status", "UP");
                healthStatus.put("database", connection.getMetaData().getDatabaseProductName());
                healthStatus.put("databaseVersion", connection.getMetaData().getDatabaseProductVersion());
                healthStatus.put("driverName", connection.getMetaData().getDriverName());
                healthStatus.put("url", connection.getMetaData().getURL());
                log.info("Oracle Database health check: UP");
            }
        } catch (Exception e) {
            /* Database connection failed - return DOWN status with error details */
            healthStatus.put("status", "DOWN");
            healthStatus.put("error", e.getMessage());
            log.error("Oracle Database health check: DOWN - {}", e.getMessage());
            return ResponseEntity.internalServerError().body(healthStatus);
        }

        return ResponseEntity.ok(healthStatus);
    }
}
