package com.javatodev.finance.model.dto.response;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/**
 * Response DTO for snapshot details.
 */
@Getter
@Setter
public class SnapshotResponse {
    private Long id;
    private String name;
    private String description;
    private Long dataspaceId;
    private String dataspaceName;
    private String createdBy;
    private Instant createdAt;
}
