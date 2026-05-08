package com.banking.oracle.exception;

/**
 * Custom exception thrown when attempting to create a customer with an email
 * that already exists in the database. Results in a 409 CONFLICT HTTP response.
 */
public class DuplicateEmailException extends RuntimeException {

    /**
     * Creates exception with a message indicating the duplicate email.
     *
     * @param email the email address that already exists
     */
    public DuplicateEmailException(String email) {
        super("Customer with email '" + email + "' already exists");
    }
}
