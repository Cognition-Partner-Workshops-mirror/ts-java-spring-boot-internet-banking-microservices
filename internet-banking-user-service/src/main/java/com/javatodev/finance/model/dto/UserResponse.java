package com.javatodev.finance.model.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Response DTO for user operations (SRP - Phase 6).
 * Excludes password for security; contains only displayable user fields.
 */
@Data
@Builder
public class UserResponse {
    private Long id;
    private String email;
    private String identification;
    private Status status;
    private String authId;
}
