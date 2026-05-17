package com.javatodev.finance.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(SimpleBankingGlobalException.class)
    protected ResponseEntity handleGlobalException(SimpleBankingGlobalException simpleBankingGlobalException, Locale locale) {
        return ResponseEntity
                .badRequest()
                .body(ErrorResponse.builder()
                        .code(simpleBankingGlobalException.getCode())
                        .message(simpleBankingGlobalException.getMessage())
                        .build());
    }

    // Handles bean validation errors and returns field-level error messages
    @ExceptionHandler(MethodArgumentNotValidException.class)
    protected ResponseEntity<Map<String, String>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
            errors.put(error.getField(), error.getDefaultMessage())
        );
        return ResponseEntity.badRequest().body(errors);
    }

    // Sanitized error response — log full details server-side, return generic message to client
    @ExceptionHandler({Exception.class})
    protected ResponseEntity<ErrorResponse> handleException(Exception e, Locale locale) {
        log.error("Unexpected error occurred", e);
        return ResponseEntity
                .internalServerError()
                .body(ErrorResponse.builder()
                        .code("INTERNAL_ERROR")
                        .message("An unexpected error occurred. Please try again later.")
                        .build());
    }

}
