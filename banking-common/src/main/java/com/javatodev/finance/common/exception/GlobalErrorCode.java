package com.javatodev.finance.common.exception;

/**
 * Centralized error codes shared across all microservices.
 */
public class GlobalErrorCode {
    public static final String ERROR_ENTITY_NOT_FOUND = "BANKING-COMMON-1000";
    public static final String INSUFFICIENT_FUNDS = "BANKING-COMMON-1001";

    private GlobalErrorCode() {
        // prevent instantiation
    }
}
