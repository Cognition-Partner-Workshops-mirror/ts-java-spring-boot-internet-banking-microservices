package com.banking.oracle.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO (Data Transfer Object) for incoming customer creation and update requests.
 * Contains validation constraints to ensure data integrity before persistence.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerRequest {

    /* Customer's first name - required field */
    @NotBlank(message = "First name is required")
    @Size(max = 100, message = "First name must not exceed 100 characters")
    private String firstName;

    /* Customer's last name - required field */
    @NotBlank(message = "Last name is required")
    @Size(max = 100, message = "Last name must not exceed 100 characters")
    private String lastName;

    /* Customer's email address - required and must be valid */
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid email address")
    private String email;

    /* Customer's phone number - optional */
    @Size(max = 20, message = "Phone number must not exceed 20 characters")
    private String phoneNumber;

    /* Customer's residential address - optional */
    @Size(max = 500, message = "Address must not exceed 500 characters")
    private String address;
}
