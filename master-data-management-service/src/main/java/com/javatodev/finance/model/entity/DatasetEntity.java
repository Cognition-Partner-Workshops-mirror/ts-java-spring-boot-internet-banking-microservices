package com.javatodev.finance.model.entity;

import com.javatodev.finance.model.dto.AuditAware;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Represents a Dataset — a named collection of tables within a Dataspace.
 * Analogous to an EBX dataset, grouping related master data tables together.
 */
@Getter
@Setter
@Entity
@Table(name = "mdm_dataset")
public class DatasetEntity extends AuditAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String description;

    /** The dataspace this dataset belongs to. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dataspace_id", nullable = false)
    private DataspaceEntity dataspace;

}
