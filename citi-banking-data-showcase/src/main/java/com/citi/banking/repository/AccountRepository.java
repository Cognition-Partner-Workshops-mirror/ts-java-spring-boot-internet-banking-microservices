package com.citi.banking.repository;

import com.citi.banking.model.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {
    Optional<Account> findByAccountNumber(String accountNumber);
    List<Account> findByCustomerId(Long customerId);
    List<Account> findByAccountType(Account.AccountType accountType);
    List<Account> findByStatus(Account.AccountStatus status);
    List<Account> findByBranchCode(String branchCode);
}
