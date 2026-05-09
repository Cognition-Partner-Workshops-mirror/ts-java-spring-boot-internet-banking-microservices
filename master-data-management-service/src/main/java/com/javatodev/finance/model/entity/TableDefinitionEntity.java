package com.javatodev.finance.model.entity;

import com.javatodev.finance.model.dto.AuditAware;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Represents a master data table definition within a Dataset.
 * Stores the table schema (name + column definitions). Records are stored separately.
 */
@Getter
@Setter
@Entity
@Table(name = "mdm_table_definition")
public class TableDefinitionEntity extends AuditAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String description;

    /** The dataset this table belongs to. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dataset_id", nullable = false)
    private DatasetEntity dataset;

    /** Column definitions for this table, ordered by ordinal. */
    @OneToMany(mappedBy = "tableDefinition", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("ordinal ASC")
    private List<ColumnDefinitionEntity> columns = new ArrayList<>();

}
