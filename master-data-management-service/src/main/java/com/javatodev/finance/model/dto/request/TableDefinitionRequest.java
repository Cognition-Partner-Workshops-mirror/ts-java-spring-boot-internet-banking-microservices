package com.javatodev.finance.model.dto.request;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * Request DTO for creating a table with column definitions.
 */
@Getter
@Setter
public class TableDefinitionRequest {
    private String name;
    private String description;
    private Long datasetId;
    private List<ColumnDefinitionRequest> columns;
}
