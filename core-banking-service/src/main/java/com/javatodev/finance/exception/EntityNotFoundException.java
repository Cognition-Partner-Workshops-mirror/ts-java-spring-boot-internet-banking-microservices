package com.javatodev.finance.exception;

/**
 * Service-specific entity-not-found exception extending the shared version.
 * Uses core-banking-service-specific error codes.
 */
public class EntityNotFoundException extends com.javatodev.finance.common.exception.EntityNotFoundException {
    public EntityNotFoundException() {
        super("Requested entity not present in the DB.", GlobalErrorCode.ERROR_ENTITY_NOT_FOUND);
    }

    public EntityNotFoundException(String message) {
        super(message, GlobalErrorCode.ERROR_ENTITY_NOT_FOUND);
    }
}
