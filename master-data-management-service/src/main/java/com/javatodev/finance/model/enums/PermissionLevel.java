package com.javatodev.finance.model.enums;

/**
 * Permission levels for resource access control.
 * READ - view data only.
 * WRITE - view and modify data.
 * ADMIN - full control including delete and permission management.
 */
public enum PermissionLevel {
    READ,
    WRITE,
    ADMIN
}
