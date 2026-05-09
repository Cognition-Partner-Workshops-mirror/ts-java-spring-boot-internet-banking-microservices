package com.javatodev.finance.model.enums;

/**
 * Status lifecycle for a master data record.
 * DRAFT - awaiting workflow approval.
 * PENDING_APPROVAL - submitted for review.
 * APPROVED - accepted and active.
 * REJECTED - declined by reviewer.
 */
public enum RecordStatus {
    DRAFT,
    PENDING_APPROVAL,
    APPROVED,
    REJECTED
}
