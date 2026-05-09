package com.javatodev.finance.model.dto.request;

import lombok.Getter;
import lombok.Setter;

/**
 * Request DTO for comparing MDM data against a target environment.
 */
@Getter
@Setter
public class EnvironmentCompareRequest {
    /** The snapshot to use as the source of truth. */
    private Long snapshotId;
    /** The target environment to compare against. */
    private Long environmentId;
    /** Optional: specific table name to compare. If null, all tables are compared. */
    private String tableName;
}
