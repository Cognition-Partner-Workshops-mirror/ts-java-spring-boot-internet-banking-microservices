package com.javatodev.finance.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * Standard error response DTO returned by the global exception handler.
 */
@Getter
@Setter
@AllArgsConstructor
public class ErrorResponse {
    private String code;
    private String message;
}
