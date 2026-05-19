package com.javatodev.finance.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for GlobalExceptionHandler to verify correct HTTP status codes.
 * Previously all exceptions returned HTTP 400 — these tests ensure:
 * - SimpleBankingGlobalException -> 400
 * - ResourceNotFoundException -> 404
 * - IllegalArgumentException -> 400
 * - Generic Exception -> 500
 */
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void handleGlobalException_shouldReturn400() {
        SimpleBankingGlobalException ex = new SimpleBankingGlobalException("ERR_001", "Insufficient funds");

        ResponseEntity<ErrorResponse> response = handler.handleGlobalException(ex, Locale.getDefault());

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("ERR_001", response.getBody().getCode());
        assertEquals("Insufficient funds", response.getBody().getMessage());
    }

    @Test
    void handleNotFound_shouldReturn404() {
        ResourceNotFoundException ex = new ResourceNotFoundException("FundTransfer", "TXN-999");

        ResponseEntity<ErrorResponse> response = handler.handleNotFound(ex, Locale.getDefault());

        // Verify: 404 instead of the old blanket 400
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("NOT_FOUND", response.getBody().getCode());
        assertTrue(response.getBody().getMessage().contains("TXN-999"));
    }

    @Test
    void handleBadRequest_shouldReturn400() {
        IllegalArgumentException ex = new IllegalArgumentException("Amount must be positive");

        ResponseEntity<ErrorResponse> response = handler.handleBadRequest(ex, Locale.getDefault());

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("BAD_REQUEST", response.getBody().getCode());
    }

    @Test
    void handleException_shouldReturn500InsteadOf400() {
        RuntimeException ex = new RuntimeException("Database connection lost");

        ResponseEntity<ErrorResponse> response = handler.handleException(ex, Locale.getDefault());

        // Verify: 500 instead of the old blanket 400
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("INTERNAL_ERROR", response.getBody().getCode());
    }
}
