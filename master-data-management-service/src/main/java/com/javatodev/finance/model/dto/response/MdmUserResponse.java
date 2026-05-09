package com.javatodev.finance.model.dto.response;

import lombok.Getter;
import lombok.Setter;

/**
 * Response DTO for MDM user details. Password is never included.
 */
@Getter
@Setter
public class MdmUserResponse {
    private Long id;
    private String username;
    private String email;
    private String role;
    private String status;
}
