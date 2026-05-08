package com.javatodev.finance.model.dto.response;

import lombok.Getter;
import lombok.Setter;

/**
 * Response DTO for a column definition.
 */
@Getter
@Setter
public class ColumnDefinitionResponse {
    private Long id;
    private String name;
    private String dataType;
    private boolean required;
    private boolean uniqueKey;
    private int ordinal;
}
