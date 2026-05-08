package com.javatodev.finance.exception;

/**
 * Thrown when a requested entity is not found in the database.
 */
public class EntityNotFoundException extends RuntimeException {

    public EntityNotFoundException() {
        super("Entity not found");
    }

    public EntityNotFoundException(String message) {
        super(message);
    }
}
