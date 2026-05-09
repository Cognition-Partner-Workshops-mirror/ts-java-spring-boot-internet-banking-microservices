package com.javatodev.finance.model.dto.response;

import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

/**
 * Response DTO for snapshot comparison results.
 * Groups differences by table name.
 */
@Getter
@Setter
public class SnapshotCompareResponse {
    private Long sourceSnapshotId;
    private Long targetSnapshotId;
    private List<TableDiff> tableDiffs;

    /**
     * Differences for a single table between two snapshots.
     */
    @Getter
    @Setter
    public static class TableDiff {
        private String tableName;
        private List<Map<String, Object>> added;
        private List<Map<String, Object>> removed;
        private List<RecordDiff> modified;
    }

    /**
     * A single record that was modified between the two snapshots.
     */
    @Getter
    @Setter
    public static class RecordDiff {
        private Map<String, Object> sourceRecord;
        private Map<String, Object> targetRecord;
        private List<String> changedFields;
    }
}
