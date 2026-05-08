package com.javatodev.finance.model.dto.request;

import lombok.Getter;
import lombok.Setter;

/**
 * Request DTO for creating or updating a Dataset.
 */
@Getter
@Setter
public class DatasetRequest {
    private String name;
    private String description;
    private Long dataspaceId;
}
