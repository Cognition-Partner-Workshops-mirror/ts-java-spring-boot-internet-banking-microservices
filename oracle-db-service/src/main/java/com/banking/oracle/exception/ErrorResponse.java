package com.banking.oracle.exception;

import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Standardized error response DTO returned by the GlobalExceptionHandler.
 * Provides a consistent error format for all API error responses.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {

    /* Timestamp when the error occurred */
    private LocalDateTime timestamp;

    /* HTTP status code (e.g., 404, 409, 500) */
    private int status;

    /* Brief error type description (e.g., "Not Found", "Conflict") */
    private String error;

    /* Detailed error message explaining what went wrong */
    private String message;

    /* The API path that triggered the error */
    private String path;

    /* List of field-level validation errors (populated for 400 Bad Request) */
    private List<String> details;
}
