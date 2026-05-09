package com.javatodev.finance.model.enums;

/**
 * Roles for MDM users controlling access levels.
 * ADMIN - full system access.
 * DATA_STEWARD - can create/modify data and approve workflows.
 * VIEWER - read-only access.
 */
public enum MdmUserRole {
    ADMIN,
    DATA_STEWARD,
    VIEWER
}
