package com.javatodev.finance.common.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Base exception for all banking-related errors across microservices.
 * Consolidated into the shared library to provide a single exception hierarchy.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class SimpleBankingGlobalException extends RuntimeException {

    private String code;
    private String message;

    public SimpleBankingGlobalException(String message) {
        super(message);
    }
}
