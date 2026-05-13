package com.ridesharing.enums;

/**
 * Supported payment methods for ride transactions.
 * CASH - Payment made in cash after ride completion.
 * CREDIT_CARD - Payment via credit card.
 * DEBIT_CARD - Payment via debit card.
 * UPI - Payment via Unified Payments Interface (popular in India).
 * WALLET - Payment via in-app wallet balance.
 */
public enum PaymentMethod {
    CASH,
    CREDIT_CARD,
    DEBIT_CARD,
    UPI,
    WALLET
}
