package com.javatodev.finance.model.dto.request;

import lombok.Getter;
import lombok.Setter;

/**
 * Request DTO for granting a permission to a user on a resource.
 */
@Getter
@Setter
public class PermissionRequest {
    private Long userId;
    /** DATASPACE, DATASET, TABLE */
    private String resourceType;
    private Long resourceId;
    /** READ, WRITE, ADMIN */
    private String level;
}
