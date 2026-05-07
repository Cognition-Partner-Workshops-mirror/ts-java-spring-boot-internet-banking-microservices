package com.javatodev.finance.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class User extends AuditAware {
    private Long id;

    @NotBlank
    @Email
    private String email;

    @NotBlank
    private String identification;

    @NotBlank
    @Size(min = 8)
    private String password;

    private String authId;

    private Status status;
}
