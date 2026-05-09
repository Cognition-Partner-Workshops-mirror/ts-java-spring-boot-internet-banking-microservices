package com.javatodev.finance.model.dto.request;

import lombok.Getter;
import lombok.Setter;

/**
 * Request DTO for registering a target environment for synchronization.
 */
@Getter
@Setter
public class EnvironmentConfigRequest {
    private String name;
    /** EBX, DIT, SIT, UAT, PROD */
    private String type;
    private String dbUrl;
    private String dbUsername;
    private String dbPassword;
}
