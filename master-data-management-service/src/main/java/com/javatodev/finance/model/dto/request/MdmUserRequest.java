package com.javatodev.finance.model.dto.request;

import lombok.Getter;
import lombok.Setter;

/**
 * Request DTO for creating or updating an MDM user.
 */
@Getter
@Setter
public class MdmUserRequest {
    private String username;
    private String email;
    private String password;
    /** ADMIN, DATA_STEWARD, VIEWER */
    private String role;
}
