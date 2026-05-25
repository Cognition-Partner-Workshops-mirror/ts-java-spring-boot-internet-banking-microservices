package com.javatodev.finance.common.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Abstract base exception handler shared across all microservices (Phase 9 security fix).
 * Not annotated with @ControllerAdvice — each service must create a concrete subclass
 * annotated with @ControllerAdvice to activate these handlers and add service-specific ones.
 * Does NOT extend ResponseEntityExceptionHandler to avoid ambiguous handler conflicts in Spring 6.
 */
public abstract class GlobalExceptionHandler {

    /**
     * Handles known banking exceptions with their specific error codes.
     */
    @ExceptionHandler(SimpleBankingGlobalException.class)
    protected ResponseEntity<ErrorResponse> handleGlobalException(SimpleBankingGlobalException e, Locale locale) {
        return ResponseEntity
            .badRequest()
            .body(ErrorResponse.builder()
                .code(e.getCode())
                .message(e.getMessage())
                .build());
    }

    /**
     * Returns HTTP 404 for entity-not-found cases instead of generic 400.
     */
    @ExceptionHandler(EntityNotFoundException.class)
    protected ResponseEntity<ErrorResponse> handleEntityNotFoundException(EntityNotFoundException e, Locale locale) {
        return ResponseEntity
            .status(404)
            .body(ErrorResponse.builder()
                .code(e.getCode())
                .message(e.getMessage())
                .build());
    }

    /**
     * Handles bean validation errors, returning field-level details.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    protected ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException e) {
        String details = e.getBindingResult().getFieldErrors().stream()
            .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
            .collect(Collectors.joining(", "));
        return ResponseEntity
            .badRequest()
            .body(ErrorResponse.builder()
                .code("VALIDATION_ERROR")
                .message(details)
                .build());
    }

    /**
     * Catch-all handler: returns a generic 500 error without leaking exception details (security fix).
     */
    @ExceptionHandler({Exception.class})
    protected ResponseEntity<ErrorResponse> handleException(Exception e, Locale locale) {
        return ResponseEntity
            .internalServerError()
            .body(ErrorResponse.builder()
                .code("INTERNAL_ERROR")
                .message("An unexpected error occurred")
                .build());
    }
}
