package com.banking.oracle.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * JPA Entity representing a Customer in the Oracle Database.
 * Maps to the CUSTOMERS table and uses an Oracle sequence for ID generation.
 * Includes audit fields (createdAt, updatedAt) that are auto-populated.
 */
@Entity
@Table(name = "CUSTOMERS")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Customer {

    /**
     * Primary key using Oracle sequence generator for auto-incrementing IDs.
     * Oracle does not support MySQL-style AUTO_INCREMENT, so we use SEQUENCE strategy.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "customer_seq")
    @SequenceGenerator(name = "customer_seq", sequenceName = "CUSTOMER_SEQ", allocationSize = 1)
    private Long id;

    /* Customer's first name - required field, max 100 characters */
    @NotBlank(message = "First name is required")
    @Size(max = 100, message = "First name must not exceed 100 characters")
    @Column(name = "FIRST_NAME", nullable = false, length = 100)
    private String firstName;

    /* Customer's last name - required field, max 100 characters */
    @NotBlank(message = "Last name is required")
    @Size(max = 100, message = "Last name must not exceed 100 characters")
    @Column(name = "LAST_NAME", nullable = false, length = 100)
    private String lastName;

    /* Customer's email address - must be unique and valid email format */
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid email address")
    @Column(name = "EMAIL", nullable = false, unique = true, length = 255)
    private String email;

    /* Customer's phone number - optional, max 20 characters */
    @Size(max = 20, message = "Phone number must not exceed 20 characters")
    @Column(name = "PHONE_NUMBER", length = 20)
    private String phoneNumber;

    /* Customer's residential address - optional, stored as VARCHAR2(500) */
    @Size(max = 500, message = "Address must not exceed 500 characters")
    @Column(name = "ADDRESS", length = 500)
    private String address;

    /* Account status: ACTIVE, INACTIVE, or SUSPENDED */
    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", length = 20)
    @Builder.Default
    private CustomerStatus status = CustomerStatus.ACTIVE;

    /* Timestamp when the customer record was created - auto-populated on persist */
    @Column(name = "CREATED_AT", updatable = false)
    private LocalDateTime createdAt;

    /* Timestamp when the customer record was last updated - auto-populated on update */
    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    /**
     * JPA lifecycle callback to set createdAt and updatedAt
     * timestamps automatically before persisting a new entity.
     */
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    /**
     * JPA lifecycle callback to update the updatedAt timestamp
     * automatically before updating an existing entity.
     */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
