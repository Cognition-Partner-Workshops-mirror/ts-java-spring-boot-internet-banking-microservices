package com.javatodev.finance.exception;

/**
 * Service-local alias for the shared ErrorResponse in banking-common.
 * Retained for backward compatibility.
 */
public class ErrorResponse extends com.javatodev.finance.common.exception.ErrorResponse {
    public ErrorResponse() {
        super();
    }

    public ErrorResponse(String code, String message) {
        super(code, message);
    }
}
