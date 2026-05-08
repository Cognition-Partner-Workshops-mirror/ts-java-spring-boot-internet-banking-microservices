package com.javatodev.finance.model.dto.request;

import lombok.Getter;
import lombok.Setter;

/**
 * Request DTO for approving or rejecting a workflow.
 */
@Getter
@Setter
public class WorkflowReviewRequest {
    private String reviewedBy;
    private String reviewComment;
}
