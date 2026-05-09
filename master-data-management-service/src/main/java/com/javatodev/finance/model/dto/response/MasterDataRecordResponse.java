package com.javatodev.finance.model.dto.response;

import java.util.Map;

import lombok.Getter;
import lombok.Setter;

/**
 * Response DTO for a master data record.
 */
@Getter
@Setter
public class MasterDataRecordResponse {
    private Long id;
    private Long tableId;
    private String tableName;
    private Map<String, Object> data;
    private String status;
    private long version;
}
