package com.javatodev.finance.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Locale;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(EntityNotFoundException.class)
    protected ResponseEntity<ErrorResponse> handleEntityNotFoundException(EntityNotFoundException e, Locale locale) {
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse.builder()
                .code(e.getCode())
                .message(e.getMessage())
                .build());
    }

    @ExceptionHandler(InsufficientFundsException.class)
    protected ResponseEntity<ErrorResponse> handleInsufficientFundsException(InsufficientFundsException e, Locale locale) {
        return ResponseEntity
            .status(HttpStatus.UNPROCESSABLE_ENTITY)
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
        log.error("Unexpected error", e);
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse.builder()
                .code(GlobalErrorCode.ERROR_INTERNAL_SERVER)
                .message("An unexpected error occurred. Please try again later.")
                .build());
    }

}
