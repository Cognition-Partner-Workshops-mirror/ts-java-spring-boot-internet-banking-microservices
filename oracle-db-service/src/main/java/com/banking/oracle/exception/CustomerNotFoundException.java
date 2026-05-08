package com.banking.oracle.exception;

/**
 * Custom exception thrown when a customer is not found in the database.
 * Results in a 404 NOT FOUND HTTP response via the GlobalExceptionHandler.
 */
public class CustomerNotFoundException extends RuntimeException {

    /**
     * Creates exception with a message indicating which customer ID was not found.
     *
     * @param id the customer ID that was not found
     */
    public CustomerNotFoundException(Long id) {
        super("Customer not found with id: " + id);
    }

    /**
     * Creates exception with a message indicating which email was not found.
     *
     * @param email the customer email that was not found
     */
    public CustomerNotFoundException(String email) {
        super("Customer not found with email: " + email);
    }
}
