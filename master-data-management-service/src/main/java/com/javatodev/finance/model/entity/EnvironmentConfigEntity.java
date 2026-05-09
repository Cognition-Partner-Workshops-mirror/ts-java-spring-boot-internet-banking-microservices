package com.javatodev.finance.model.entity;

import com.javatodev.finance.model.dto.AuditAware;
import com.javatodev.finance.model.enums.EnvironmentType;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Configuration for a target environment used in data synchronization.
 * Stores JDBC connection details for comparing MDM data against external databases.
 */
@Getter
@Setter
@Entity
@Table(name = "mdm_environment_config")
public class EnvironmentConfigEntity extends AuditAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Display name for the environment, e.g., "EBX_PROD", "DIT". */
    @Column(nullable = false, unique = true)
    private String name;

    /** Type classification of the environment. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EnvironmentType type;

    /** JDBC URL of the target database. */
    @Column(nullable = false)
    private String dbUrl;

    /** Database username for the target environment. */
    @Column(nullable = false)
    private String dbUsername;

    /** Database password for the target environment. */
    @Column(nullable = false)
    private String dbPassword;

}
