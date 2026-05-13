package com.ridesharing.exception;

/**
 * Exception thrown when a client request is invalid or cannot be processed.
 */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
