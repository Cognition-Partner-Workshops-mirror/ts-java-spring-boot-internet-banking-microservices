package com.javatodev.finance.model.dto.response;

import lombok.Getter;
import lombok.Setter;

/**
 * Response DTO for a permission assignment.
 */
@Getter
@Setter
public class PermissionResponse {
    private Long id;
    private Long userId;
    private String username;
    private String resourceType;
    private Long resourceId;
    private String level;
}
