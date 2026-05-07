package com.citi.banking.repository;

import com.citi.banking.model.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    Optional<Transaction> findByTransactionId(String transactionId);
    Page<Transaction> findByAccountId(Long accountId, Pageable pageable);
    List<Transaction> findByAccountIdAndTransactionDateBetween(Long accountId, LocalDateTime start, LocalDateTime end);
    List<Transaction> findByType(Transaction.TransactionType type);
    Page<Transaction> findByAccountAccountNumber(String accountNumber, Pageable pageable);
}
