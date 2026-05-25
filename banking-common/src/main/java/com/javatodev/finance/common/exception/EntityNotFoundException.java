package com.javatodev.finance.common.exception;

/**
 * Thrown when a requested entity is not found in the database.
 * Uses a generic error code; services can subclass or pass service-specific codes.
 */
public class EntityNotFoundException extends SimpleBankingGlobalException {
    public EntityNotFoundException() {
        super("Requested entity not present in the DB.", "ENTITY_NOT_FOUND");
    }

    public EntityNotFoundException(String message) {
        super(message, "ENTITY_NOT_FOUND");
    }

    public EntityNotFoundException(String message, String code) {
        super(message, code);
    }
}
