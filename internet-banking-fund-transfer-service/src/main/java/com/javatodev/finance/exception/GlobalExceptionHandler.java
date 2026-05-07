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

    @ExceptionHandler(SimpleBankingGlobalException.class)
    protected ResponseEntity<ErrorResponse> handleGlobalException(SimpleBankingGlobalException e, Locale locale) {
        return ResponseEntity
            .badRequest()
            .body(new ErrorResponse(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler({Exception.class})
    protected ResponseEntity<ErrorResponse> handleException(Exception e, Locale locale) {
        log.error("Unexpected error", e);
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ErrorResponse("FUND-TRANSFER-SERVICE-1999", "An unexpected error occurred. Please try again later."));
    }
}
