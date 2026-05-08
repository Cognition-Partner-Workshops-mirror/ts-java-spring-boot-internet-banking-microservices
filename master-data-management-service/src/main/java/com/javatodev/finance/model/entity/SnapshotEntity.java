package com.javatodev.finance.model.entity;

import java.time.Instant;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Represents a point-in-time snapshot of a Dataspace.
 * Captures all approved records across all tables at the moment of creation.
 * Snapshots are immutable (read-only) once created.
 */
@Getter
@Setter
@Entity
@Table(name = "mdm_snapshot")
public class SnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String description;

    /** The dataspace this snapshot was taken from. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dataspace_id", nullable = false)
    private DataspaceEntity dataspace;

    private String createdBy;

    private Instant createdAt;

}
