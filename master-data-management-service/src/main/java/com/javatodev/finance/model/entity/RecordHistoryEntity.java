package com.javatodev.finance.model.entity;

import java.time.Instant;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Audit trail entry for master data record changes.
 * Captures the previous and new state of a record for each change event.
 */
@Getter
@Setter
@Entity
@Table(name = "mdm_record_history")
public class RecordHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The record that was changed. */
    private Long recordId;

    /** The table the record belongs to. */
    private Long tableDefinitionId;

    /** JSON snapshot of the record before the change. */
    @Column(columnDefinition = "LONGTEXT")
    private String previousData;

    /** JSON snapshot of the record after the change. */
    @Column(columnDefinition = "LONGTEXT")
    private String newData;

    /** Type of change: INSERT, UPDATE, DELETE */
    private String changeType;

    /** User who made the change. */
    private String changedBy;

    /** Timestamp of the change. */
    private Instant changedAt;

}
