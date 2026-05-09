package com.javatodev.finance.model.dto.response;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/**
 * Response DTO for a record audit history entry.
 */
@Getter
@Setter
public class RecordHistoryResponse {
    private Long id;
    private Long recordId;
    private String previousData;
    private String newData;
    private String changeType;
    private String changedBy;
    private Instant changedAt;
}
