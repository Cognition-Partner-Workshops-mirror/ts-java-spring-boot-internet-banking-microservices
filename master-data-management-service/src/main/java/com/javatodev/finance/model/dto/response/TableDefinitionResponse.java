package com.javatodev.finance.model.dto.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * Response DTO for a table definition including its columns.
 */
@Getter
@Setter
public class TableDefinitionResponse {
    private Long id;
    private String name;
    private String description;
    private Long datasetId;
    private String datasetName;
    private List<ColumnDefinitionResponse> columns;
    private long version;
}
