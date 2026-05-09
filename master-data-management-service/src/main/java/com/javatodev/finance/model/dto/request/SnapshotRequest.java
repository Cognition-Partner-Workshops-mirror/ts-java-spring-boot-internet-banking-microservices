package com.javatodev.finance.model.dto.request;

import lombok.Getter;
import lombok.Setter;

/**
 * Request DTO for creating a snapshot of a dataspace.
 */
@Getter
@Setter
public class SnapshotRequest {
    private Long dataspaceId;
    private String name;
    private String description;
}
