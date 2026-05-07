package com.citi.banking.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "credit_cards")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreditCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 16)
    private String cardNumber;

    @Enumerated(EnumType.STRING)
    @NotNull
    @Column(nullable = false)
    private CardProduct cardProduct;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal creditLimit;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal currentBalance;

    @Column(precision = 15, scale = 2)
    private BigDecimal availableCredit;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal apr;

    @Column(precision = 15, scale = 2)
    private BigDecimal minimumPaymentDue;

    private LocalDate paymentDueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CardStatus status;

    private LocalDate expirationDate;

    @Column(precision = 10, scale = 0)
    private BigDecimal rewardPoints;

    @Column(nullable = false, updatable = false)
    private LocalDateTime issuedAt;

    @PrePersist
    protected void onCreate() {
        issuedAt = LocalDateTime.now();
    }

    public enum CardProduct {
        CITI_DOUBLE_CASH,
        CITI_CUSTOM_CASH,
        CITI_PREMIER,
        CITI_STRATA_PREMIER,
        CITI_REWARDS_PLUS,
        CITI_DIAMOND_PREFERRED,
        CITI_SECURED,
        CITI_BUSINESS_PLATINUM,
        COSTCO_ANYWHERE_VISA
    }

    public enum CardStatus {
        ACTIVE,
        SUSPENDED,
        CANCELED,
        EXPIRED,
        LOST_STOLEN
    }
}
