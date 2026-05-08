package com.banking.oracle.model.dto;

import java.time.LocalDateTime;

import com.banking.oracle.model.CustomerStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO (Data Transfer Object) for outgoing customer API responses.
 * Decouples the API response format from the internal JPA entity structure.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerResponse {

    /* Unique customer identifier */
    private Long id;

    /* Customer's first name */
    private String firstName;

    /* Customer's last name */
    private String lastName;

    /* Customer's email address */
    private String email;

    /* Customer's phone number */
    private String phoneNumber;

    /* Customer's residential address */
    private String address;

    /* Current account status (ACTIVE, INACTIVE, SUSPENDED) */
    private CustomerStatus status;

    /* Timestamp of record creation */
    private LocalDateTime createdAt;

    /* Timestamp of last record update */
    private LocalDateTime updatedAt;
}
