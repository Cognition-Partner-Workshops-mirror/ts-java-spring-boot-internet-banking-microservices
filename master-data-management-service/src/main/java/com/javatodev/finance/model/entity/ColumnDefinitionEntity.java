package com.javatodev.finance.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Defines a single column within a master data table.
 * Specifies the column name, data type, constraints, and ordering.
 */
@Getter
@Setter
@Entity
@Table(name = "mdm_column_definition")
public class ColumnDefinitionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    /** Data type: STRING, INTEGER, DECIMAL, DATE, BOOLEAN */
    @Column(nullable = false)
    private String dataType;

    /** Whether this column is mandatory. */
    private boolean required;

    /** Whether this column must have unique values across records. */
    private boolean uniqueKey;

    /** Display/processing order of this column. */
    private int ordinal;

    /** The table this column belongs to. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "table_definition_id", nullable = false)
    private TableDefinitionEntity tableDefinition;

}
