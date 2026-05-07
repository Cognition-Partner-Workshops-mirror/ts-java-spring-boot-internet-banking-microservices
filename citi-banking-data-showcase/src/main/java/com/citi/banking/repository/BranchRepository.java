package com.citi.banking.repository;

import com.citi.banking.model.Branch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BranchRepository extends JpaRepository<Branch, Long> {
    Optional<Branch> findByBranchCode(String branchCode);
    List<Branch> findByCity(String city);
    List<Branch> findByState(String state);
    List<Branch> findByBranchType(Branch.BranchType branchType);
    List<Branch> findByStatus(Branch.BranchStatus status);
}
