package com.citi.banking.repository;

import com.citi.banking.model.Loan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LoanRepository extends JpaRepository<Loan, Long> {
    Optional<Loan> findByLoanNumber(String loanNumber);
    List<Loan> findByCustomerId(Long customerId);
    List<Loan> findByLoanType(Loan.LoanType loanType);
    List<Loan> findByStatus(Loan.LoanStatus status);
}
