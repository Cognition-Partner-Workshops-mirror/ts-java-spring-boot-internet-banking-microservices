package com.javatodev.finance.repository;

import com.javatodev.finance.model.AccountStatus;
import com.javatodev.finance.model.AccountType;
import com.javatodev.finance.model.entity.BankAccountEntity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BankAccountRepository extends JpaRepository<BankAccountEntity, Long> {
    Optional<BankAccountEntity> findByNumber(String accountNumber);

    // Find accounts filtered by status (ACTIVE, DORMANT, etc.)
    List<BankAccountEntity> findByStatus(AccountStatus status);

    // Find accounts filtered by type (SAVINGS_ACCOUNT, FIXED_DEPOSIT, LOAN_ACCOUNT)
    List<BankAccountEntity> findByType(AccountType type);

    // Find accounts filtered by both status and type
    List<BankAccountEntity> findByStatusAndType(AccountStatus status, AccountType type);
}
