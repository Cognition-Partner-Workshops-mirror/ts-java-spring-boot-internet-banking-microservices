package com.javatodev.finance.model.dto.request;

import java.time.LocalDate;

import lombok.Data;

@Data
public class MemberCreateRequest {

    private String firstName;
    private String lastName;
    private String email;
    private String phoneNumber;
    private String identificationNumber;
    private String membershipType;
    private String address;
    private LocalDate dateOfBirth;

}
