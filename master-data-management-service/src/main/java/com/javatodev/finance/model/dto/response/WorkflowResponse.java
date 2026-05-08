package com.javatodev.finance.model.dto.response;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/**
 * Response DTO for a workflow approval request.
 */
@Getter
@Setter
public class WorkflowResponse {
    private Long id;
    private Long recordId;
    private Long tableDefinitionId;
    private String changeType;
    private String proposedData;
    private String previousData;
    private String status;
    private String requestedBy;
    private String reviewedBy;
    private String reviewComment;
    private Instant reviewedAt;
}
