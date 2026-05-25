package com.javatodev.finance.model.dto;

import jakarta.persistence.MappedSuperclass;

/**
 * Service-local alias for the shared AuditAware in banking-common.
 * Retained so existing entities/DTOs compile without import changes.
 */
@MappedSuperclass
public class AuditAware extends com.javatodev.finance.common.audit.AuditAware {
}
