package com.javatodev.finance.model.dto.request;

import lombok.Getter;
import lombok.Setter;

/**
 * Request DTO for comparing two snapshots.
 */
@Getter
@Setter
public class SnapshotCompareRequest {
    private Long sourceSnapshotId;
    private Long targetSnapshotId;
}
