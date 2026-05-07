package com.citi.banking.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "accounts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 20)
    private String accountNumber;

    @Enumerated(EnumType.STRING)
    @NotNull
    @Column(nullable = false)
    private AccountType accountType;

    @Enumerated(EnumType.STRING)
    @NotNull
    @Column(nullable = false)
    private AccountStatus status;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal balance;

    @Column(precision = 15, scale = 2)
    private BigDecimal availableBalance;

    @Column(nullable = false)
    private String currency;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(nullable = false)
    private String branchCode;

    @Column(nullable = false, updatable = false)
    private LocalDateTime openedAt;

    private LocalDateTime closedAt;

    @PrePersist
    protected void onCreate() {
        openedAt = LocalDateTime.now();
    }

    public enum AccountType {
        CHECKING,
        SAVINGS,
        MONEY_MARKET,
        CERTIFICATE_OF_DEPOSIT,
        BUSINESS_CHECKING,
        BUSINESS_SAVINGS
    }

    public enum AccountStatus {
        ACTIVE,
        DORMANT,
        FROZEN,
        CLOSED
    }
}
