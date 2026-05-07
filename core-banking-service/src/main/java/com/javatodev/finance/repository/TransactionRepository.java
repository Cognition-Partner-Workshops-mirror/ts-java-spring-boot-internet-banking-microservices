package com.javatodev.finance.repository;

import com.javatodev.finance.model.TransactionType;
import com.javatodev.finance.model.entity.TransactionEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TransactionRepository extends JpaRepository<TransactionEntity, Long> {

    // Find all transactions for a specific bank account number
    @Query("SELECT t FROM TransactionEntity t WHERE t.account.number = :accountNumber")
    List<TransactionEntity> findByAccountNumber(@Param("accountNumber") String accountNumber);

    // Find all transactions filtered by transaction type (FUND_TRANSFER or UTILITY_PAYMENT)
    List<TransactionEntity> findByTransactionType(TransactionType transactionType);

    // Find all transactions for a given account number and transaction type
    @Query("SELECT t FROM TransactionEntity t WHERE t.account.number = :accountNumber AND t.transactionType = :type")
    List<TransactionEntity> findByAccountNumberAndType(
        @Param("accountNumber") String accountNumber,
        @Param("type") TransactionType type
    );
}
