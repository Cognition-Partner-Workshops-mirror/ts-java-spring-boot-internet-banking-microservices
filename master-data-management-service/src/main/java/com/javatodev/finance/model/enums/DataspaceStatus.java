package com.javatodev.finance.model.enums;

/**
 * Status lifecycle for a Dataspace.
 * OPEN - actively receiving data changes.
 * CLOSED - frozen, no further changes allowed.
 * MERGED - merged into parent dataspace.
 */
public enum DataspaceStatus {
    OPEN,
    CLOSED,
    MERGED
}
