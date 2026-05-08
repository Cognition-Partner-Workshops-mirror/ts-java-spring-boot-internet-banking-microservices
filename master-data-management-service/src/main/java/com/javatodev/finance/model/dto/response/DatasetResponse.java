package com.javatodev.finance.model.dto.response;

import lombok.Getter;
import lombok.Setter;

/**
 * Response DTO for Dataset details.
 */
@Getter
@Setter
public class DatasetResponse {
    private Long id;
    private String name;
    private String description;
    private Long dataspaceId;
    private String dataspaceName;
    private long version;
}
