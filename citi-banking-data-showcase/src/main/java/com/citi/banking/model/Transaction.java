package com.citi.banking.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String transactionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Enumerated(EnumType.STRING)
    @NotNull
    @Column(nullable = false)
    private TransactionType type;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(precision = 15, scale = 2)
    private BigDecimal balanceAfter;

    @Column(nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionStatus status;

    private String referenceNumber;
    private String counterpartyAccount;
    private String counterpartyName;

    @Enumerated(EnumType.STRING)
    private TransactionChannel channel;

    @Column(nullable = false, updatable = false)
    private LocalDateTime transactionDate;

    @PrePersist
    protected void onCreate() {
        transactionDate = LocalDateTime.now();
    }

    public enum TransactionType {
        DEPOSIT,
        WITHDRAWAL,
        TRANSFER_IN,
        TRANSFER_OUT,
        WIRE_TRANSFER,
        ACH_CREDIT,
        ACH_DEBIT,
        BILL_PAYMENT,
        FEE,
        INTEREST_CREDIT,
        ATM_WITHDRAWAL,
        POS_PURCHASE,
        CHECK_DEPOSIT
    }

    public enum TransactionStatus {
        COMPLETED,
        PENDING,
        FAILED,
        REVERSED
    }

    public enum TransactionChannel {
        ONLINE,
        MOBILE,
        BRANCH,
        ATM,
        WIRE,
        ACH
    }
}
