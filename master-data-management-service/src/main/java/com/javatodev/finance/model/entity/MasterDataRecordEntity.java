package com.javatodev.finance.model.entity;

import com.javatodev.finance.model.dto.AuditAware;
import com.javatodev.finance.model.enums.RecordStatus;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Represents a single row of master data within a table.
 * Data is stored as a JSON map of column-name to value.
 * Status tracks the record through the approval workflow.
 */
@Getter
@Setter
@Entity
@Table(name = "mdm_record")
public class MasterDataRecordEntity extends AuditAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The table this record belongs to. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "table_definition_id", nullable = false)
    private TableDefinitionEntity tableDefinition;

    /** Record data stored as JSON: {"column_name": "value", ...} */
    @Column(columnDefinition = "JSON")
    private String data;

    /** Current approval status of this record. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecordStatus status;

}
