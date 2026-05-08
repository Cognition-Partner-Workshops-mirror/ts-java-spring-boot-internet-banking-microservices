package com.javatodev.finance.model.dto.request;

import lombok.Getter;
import lombok.Setter;

/**
 * Request DTO for creating or updating a Dataspace.
 */
@Getter
@Setter
public class DataspaceRequest {
    private String name;
    private String description;
    /** Optional parent dataspace ID for branching. */
    private Long parentDataspaceId;
}
