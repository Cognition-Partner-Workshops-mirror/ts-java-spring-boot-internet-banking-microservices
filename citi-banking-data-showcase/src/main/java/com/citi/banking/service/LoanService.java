package com.citi.banking.service;

import com.citi.banking.model.Loan;
import com.citi.banking.repository.LoanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LoanService {

    private final LoanRepository loanRepository;

    public Page<Loan> getAllLoans(Pageable pageable) {
        return loanRepository.findAll(pageable);
    }

    public Loan getLoanById(Long id) {
        return loanRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Loan not found with id: " + id));
    }

    public Loan getLoanByNumber(String loanNumber) {
        return loanRepository.findByLoanNumber(loanNumber)
                .orElseThrow(() -> new RuntimeException("Loan not found: " + loanNumber));
    }

    public List<Loan> getLoansByCustomerId(Long customerId) {
        return loanRepository.findByCustomerId(customerId);
    }

    public List<Loan> getLoansByType(Loan.LoanType type) {
        return loanRepository.findByLoanType(type);
    }

    public List<Loan> getLoansByStatus(Loan.LoanStatus status) {
        return loanRepository.findByStatus(status);
    }
}
