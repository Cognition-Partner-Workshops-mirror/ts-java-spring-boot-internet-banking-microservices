package com.javatodev.finance.model.entity;

import com.javatodev.finance.model.dto.AuditAware;
import com.javatodev.finance.model.enums.DataspaceStatus;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Represents an EBX-style Dataspace — an isolated versioned environment for master data.
 * Each release or project gets its own dataspace. Supports parent-child branching.
 */
@Getter
@Setter
@Entity
@Table(name = "mdm_dataspace")
public class DataspaceEntity extends AuditAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    private String description;

    /** Reference to a parent dataspace for branching; null for root dataspaces. */
    private Long parentDataspaceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DataspaceStatus status;

}
