package com.citi.banking.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.time.LocalTime;

@Entity
@Table(name = "branches")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Branch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 10)
    private String branchCode;

    @NotBlank
    @Column(nullable = false)
    private String branchName;

    @Column(nullable = false)
    private String address;

    @Column(nullable = false)
    private String city;

    @Column(nullable = false)
    private String state;

    @Column(nullable = false, length = 10)
    private String zipCode;

    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BranchType branchType;

    private LocalTime openTime;
    private LocalTime closeTime;

    private Boolean hasAtm;
    private Boolean hasSafeDeposit;
    private Boolean hasNotaryService;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BranchStatus status;

    public enum BranchType {
        FULL_SERVICE,
        EXPRESS,
        WEALTH_CENTER,
        PRIVATE_BANK,
        COMMERCIAL
    }

    public enum BranchStatus {
        OPEN,
        TEMPORARILY_CLOSED,
        PERMANENTLY_CLOSED
    }
}
