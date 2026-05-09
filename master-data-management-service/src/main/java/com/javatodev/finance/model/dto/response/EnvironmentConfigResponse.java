package com.javatodev.finance.model.dto.response;

import lombok.Getter;
import lombok.Setter;

/**
 * Response DTO for environment configuration. Password is masked.
 */
@Getter
@Setter
public class EnvironmentConfigResponse {
    private Long id;
    private String name;
    private String type;
    private String dbUrl;
    private String dbUsername;
}
