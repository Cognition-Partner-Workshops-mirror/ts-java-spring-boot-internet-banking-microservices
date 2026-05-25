package com.javatodev.finance.exception;

/**
 * Service-specific alias extending the shared SimpleBankingGlobalException.
 * Maintained for backward compatibility with existing service-level exception subclasses.
 */
public class SimpleBankingGlobalException extends com.javatodev.finance.common.exception.SimpleBankingGlobalException {

    public SimpleBankingGlobalException() {
        super();
    }

    public SimpleBankingGlobalException(String code, String message) {
        super(code, message);
    }

    public SimpleBankingGlobalException(String message) {
        super(message);
    }
}
