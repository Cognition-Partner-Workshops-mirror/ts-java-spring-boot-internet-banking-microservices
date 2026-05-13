package com.ridesharing.exception;

/**
 * Exception thrown when a user attempts an action they are not authorized to perform.
 */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
