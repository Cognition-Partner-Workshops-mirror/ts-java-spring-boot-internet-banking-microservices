package com.javatodev.finance.model.entity;

import com.javatodev.finance.model.enums.PermissionLevel;
import com.javatodev.finance.model.enums.ResourceType;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Grants a specific permission level to an MDM user on a particular resource
 * (dataspace, dataset, or table).
 */
@Getter
@Setter
@Entity
@Table(name = "mdm_permission")
public class PermissionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The user receiving the permission. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private MdmUserEntity user;

    /** The type of resource being secured. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ResourceType resourceType;

    /** The ID of the specific resource instance. */
    @Column(nullable = false)
    private Long resourceId;

    /** The level of access granted. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PermissionLevel level;

}
