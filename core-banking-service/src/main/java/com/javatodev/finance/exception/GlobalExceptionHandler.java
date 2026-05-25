package com.javatodev.finance.exception;

import com.javatodev.finance.common.exception.ErrorResponse;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.Locale;

/**
 * Core-banking-service exception handler extending the shared base handler.
 * Adds service-specific handler for InsufficientFundsException (HTTP 422).
 */
@ControllerAdvice
public class GlobalExceptionHandler extends com.javatodev.finance.common.exception.GlobalExceptionHandler {

    /**
     * Returns HTTP 422 for insufficient-funds cases.
     */
    @ExceptionHandler(InsufficientFundsException.class)
    protected ResponseEntity<ErrorResponse> handleInsufficientFundsException(InsufficientFundsException e, Locale locale) {
        return ResponseEntity
            .unprocessableEntity()
            .body(ErrorResponse.builder()
                .code(e.getCode())
                .message(e.getMessage())
                .build());
    }
}
