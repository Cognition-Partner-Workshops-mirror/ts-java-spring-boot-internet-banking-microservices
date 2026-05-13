package com.ridesharing.enums;

/**
 * Represents the status of a payment transaction.
 * PENDING - Payment has been initiated but not yet processed.
 * COMPLETED - Payment was successfully processed.
 * FAILED - Payment processing failed.
 * REFUNDED - Payment was refunded to the rider.
 */
public enum PaymentStatus {
    PENDING,
    COMPLETED,
    FAILED,
    REFUNDED
}
