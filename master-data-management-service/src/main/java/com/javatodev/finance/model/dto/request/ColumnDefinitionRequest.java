package com.javatodev.finance.model.dto.request;

import lombok.Getter;
import lombok.Setter;

/**
 * Request DTO for defining a column within a table.
 */
@Getter
@Setter
public class ColumnDefinitionRequest {
    private String name;
    /** Supported types: STRING, INTEGER, DECIMAL, DATE, BOOLEAN */
    private String dataType;
    private boolean required;
    private boolean uniqueKey;
    private int ordinal;
}
