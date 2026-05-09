package com.javatodev.finance.model.entity;

import com.javatodev.finance.model.dto.AuditAware;
import com.javatodev.finance.model.enums.MdmUserRole;
import com.javatodev.finance.model.enums.MdmUserStatus;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Represents an MDM platform user with role-based access.
 * Roles: ADMIN, DATA_STEWARD, VIEWER.
 */
@Getter
@Setter
@Entity
@Table(name = "mdm_user")
public class MdmUserEntity extends AuditAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false, unique = true)
    private String email;

    /** BCrypt-hashed password. */
    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MdmUserRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MdmUserStatus status;

}
