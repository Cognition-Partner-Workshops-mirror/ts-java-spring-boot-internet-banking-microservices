package com.javatodev.finance.exception;

import org.springframework.web.bind.annotation.ControllerAdvice;

/**
 * User-service exception handler extending the shared base handler.
 * Inherits handlers for SimpleBankingGlobalException, EntityNotFoundException,
 * MethodArgumentNotValidException, and catch-all from the abstract base class.
 * Service-specific exception handlers (e.g., UserAlreadyRegisteredException) can be added here.
 */
@ControllerAdvice
public class GlobalExceptionHandler extends com.javatodev.finance.common.exception.GlobalExceptionHandler {
}
