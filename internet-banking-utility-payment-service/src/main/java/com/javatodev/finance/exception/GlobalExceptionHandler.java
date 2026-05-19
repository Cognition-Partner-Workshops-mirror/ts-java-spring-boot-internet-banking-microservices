package com.javatodev.finance.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Locale;

/**
 * Centralized exception handler for utility-payment-service.
 * Fixed: previously returned HTTP 400 for all errors including 404s and 500s.
 * Now maps exceptions to semantically correct HTTP status codes.
 */
@ControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    // Handle domain-specific exceptions with their error code
    @ExceptionHandler(SimpleBankingGlobalException.class)
    protected ResponseEntity<ErrorResponse> handleGlobalException(SimpleBankingGlobalException simpleBankingGlobalException, Locale locale) {
        return ResponseEntity
            .badRequest()
            .body(ErrorResponse.builder()
                .code(simpleBankingGlobalException.getCode())
                .message(simpleBankingGlobalException.getMessage())
                .build());
    }

    // Handle resource-not-found with HTTP 404 instead of 400
    @ExceptionHandler(ResourceNotFoundException.class)
    protected ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException e, Locale locale) {
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse.builder()
                .code("NOT_FOUND")
                .message(e.getMessage())
                .build());
    }

    // Handle illegal argument / validation errors with HTTP 400
    @ExceptionHandler(IllegalArgumentException.class)
    protected ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException e, Locale locale) {
        return ResponseEntity
            .badRequest()
            .body(ErrorResponse.builder()
                .code("BAD_REQUEST")
                .message(e.getMessage())
                .build());
    }

    // Catch-all: return HTTP 500 instead of 400 for unexpected errors
    @ExceptionHandler({Exception.class})
    protected ResponseEntity<ErrorResponse> handleException(Exception e, Locale locale) {
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse.builder()
                .code("INTERNAL_ERROR")
                .message("An unexpected error occurred: " + e.getMessage())
                .build());
    }
}
