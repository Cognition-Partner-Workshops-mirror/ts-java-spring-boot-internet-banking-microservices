package com.javatodev.finance.model.dto.response;

import lombok.Getter;
import lombok.Setter;

/**
 * Response DTO for Dataspace details.
 */
@Getter
@Setter
public class DataspaceResponse {
    private Long id;
    private String name;
    private String description;
    private Long parentDataspaceId;
    private String status;
    private long version;
}
