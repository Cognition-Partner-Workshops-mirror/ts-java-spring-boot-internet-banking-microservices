package com.javatodev.finance.model.dto.response;

import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

/**
 * Response DTO for environment comparison results (EBX vs DIT).
 * Contains diff by table and the generated SQL script.
 */
@Getter
@Setter
public class EnvironmentCompareResponse {
    private Long snapshotId;
    private Long environmentId;
    private String environmentName;
    private List<TableSyncDiff> tableDiffs;
    /** Generated SQL script to sync the target environment. */
    private String generatedScript;

    /**
     * Differences for a single table between MDM snapshot and target environment.
     */
    @Getter
    @Setter
    public static class TableSyncDiff {
        private String tableName;
        private int insertCount;
        private int updateCount;
        private int deleteCount;
        private List<Map<String, Object>> inserts;
        private List<Map<String, Object>> updates;
        private List<Map<String, Object>> deletes;
    }
}
