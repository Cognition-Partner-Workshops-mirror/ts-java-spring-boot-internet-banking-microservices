package com.javatodev.finance.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Stores the serialized record data for a specific table within a snapshot.
 * recordsJson contains a JSON array of all approved records at snapshot time.
 */
@Getter
@Setter
@Entity
@Table(name = "mdm_snapshot_data")
public class SnapshotDataEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The snapshot this data belongs to. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "snapshot_id", nullable = false)
    private SnapshotEntity snapshot;

    /** The table definition ID for reference. */
    private Long tableDefinitionId;

    /** The table name at the time of snapshot (denormalized for readability). */
    private String tableName;

    /** JSON array of all approved records: [{"col1": "val1", ...}, ...] */
    @Column(columnDefinition = "LONGTEXT")
    private String recordsJson;

}
