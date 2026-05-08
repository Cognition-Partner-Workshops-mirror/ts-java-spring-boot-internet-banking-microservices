package com.banking.oracle.model;

/**
 * Enum representing the possible statuses for a customer account.
 * Stored as a VARCHAR2 string in the Oracle CUSTOMERS table.
 */
public enum CustomerStatus {
    /* Customer account is active and in good standing */
    ACTIVE,
    /* Customer account is inactive (e.g., voluntarily closed) */
    INACTIVE,
    /* Customer account is suspended (e.g., due to policy violation) */
    SUSPENDED
}
