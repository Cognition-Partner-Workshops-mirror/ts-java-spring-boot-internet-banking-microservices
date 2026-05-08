package com.javatodev.finance.model.dto.request;

import java.util.Map;

import lombok.Getter;
import lombok.Setter;

/**
 * Request DTO for inserting or updating a master data record.
 */
@Getter
@Setter
public class MasterDataRecordRequest {
    private Long tableId;
    /** Map of column name to value. */
    private Map<String, Object> data;
}
