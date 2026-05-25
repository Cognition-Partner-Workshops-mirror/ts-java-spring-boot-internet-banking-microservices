package com.javatodev.finance.exception;

import org.springframework.web.bind.annotation.ControllerAdvice;

/**
 * Fund-transfer-service exception handler extending the shared base handler.
 * Inherits handlers for SimpleBankingGlobalException, EntityNotFoundException,
 * MethodArgumentNotValidException, and catch-all from the abstract base class.
 */
@ControllerAdvice
public class GlobalExceptionHandler extends com.javatodev.finance.common.exception.GlobalExceptionHandler {
}
