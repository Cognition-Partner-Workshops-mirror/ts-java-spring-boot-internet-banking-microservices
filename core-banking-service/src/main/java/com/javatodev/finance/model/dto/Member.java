package com.javatodev.finance.model.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Data;

@Data
public class Member {

    private Long id;
    private String firstName;
    private String lastName;
    private String email;
    private String phoneNumber;
    private String identificationNumber;
    private String membershipType;
    private String address;
    private LocalDate dateOfBirth;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
