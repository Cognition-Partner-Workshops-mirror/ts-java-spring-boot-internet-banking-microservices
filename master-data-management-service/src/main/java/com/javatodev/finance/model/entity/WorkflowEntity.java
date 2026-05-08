package com.javatodev.finance.model.entity;

import com.javatodev.finance.model.dto.AuditAware;
import com.javatodev.finance.model.enums.ChangeType;
import com.javatodev.finance.model.enums.WorkflowStatus;

import java.time.Instant;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Tracks a data change request through the maker-checker approval workflow.
 * Created when a record is inserted, updated, or deleted.
 * Must be approved or rejected by a data steward or admin.
 */
@Getter
@Setter
@Entity
@Table(name = "mdm_workflow")
public class WorkflowEntity extends AuditAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The record being changed (null for new inserts until approved). */
    private Long recordId;

    /** The table the record belongs to. */
    private Long tableDefinitionId;

    /** The type of change being requested. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChangeType changeType;

    /** JSON of the proposed new data. */
    @Column(columnDefinition = "JSON")
    private String proposedData;

    /** JSON of the previous data (for updates/deletes). */
    @Column(columnDefinition = "JSON")
    private String previousData;

    /** Current workflow status. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WorkflowStatus status;

    /** User who requested the change. */
    private String requestedBy;

    /** User who reviewed (approved/rejected) the change. */
    private String reviewedBy;

    /** Comment from the reviewer. */
    private String reviewComment;

    /** Timestamp when the review was completed. */
    private Instant reviewedAt;

}
