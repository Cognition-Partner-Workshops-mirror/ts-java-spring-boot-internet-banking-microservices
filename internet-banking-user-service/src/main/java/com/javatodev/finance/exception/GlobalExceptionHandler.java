package com.javatodev.finance.exception;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Locale;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
            .collect(Collectors.joining(", "));
        return ResponseEntity
            .badRequest()
            .body(ErrorResponse.builder()
                .code("VALIDATION_ERROR")
                .message(message)
                .build());
    }

    @ExceptionHandler(EntityNotFoundException.class)
    protected ResponseEntity<ErrorResponse> handleEntityNotFoundException(EntityNotFoundException e, Locale locale) {
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse.builder()
                .code(e.getCode())
                .message(e.getMessage())
                .build());
    }

    @ExceptionHandler(SimpleBankingGlobalException.class)
    protected ResponseEntity<ErrorResponse> handleGlobalException(SimpleBankingGlobalException simpleBankingGlobalException, Locale locale) {
        return ResponseEntity
            .badRequest()
            .body(ErrorResponse.builder()
                .code(simpleBankingGlobalException.getCode())
                .message(simpleBankingGlobalException.getMessage())
                .build());
    }

    @ExceptionHandler({Exception.class})
    protected ResponseEntity<ErrorResponse> handleException(Exception e, Locale locale) {
        log.error("Unhandled exception occurred", e);
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse.builder()
                .code("INTERNAL_ERROR")
                .message("An internal error occurred")
                .build());
    }

}
