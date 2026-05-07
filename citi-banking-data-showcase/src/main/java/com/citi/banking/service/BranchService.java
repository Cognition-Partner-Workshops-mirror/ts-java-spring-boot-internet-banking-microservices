package com.citi.banking.service;

import com.citi.banking.model.Branch;
import com.citi.banking.repository.BranchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BranchService {

    private final BranchRepository branchRepository;

    public Page<Branch> getAllBranches(Pageable pageable) {
        return branchRepository.findAll(pageable);
    }

    public Branch getBranchById(Long id) {
        return branchRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Branch not found with id: " + id));
    }

    public Branch getBranchByCode(String branchCode) {
        return branchRepository.findByBranchCode(branchCode)
                .orElseThrow(() -> new RuntimeException("Branch not found: " + branchCode));
    }

    public List<Branch> getBranchesByCity(String city) {
        return branchRepository.findByCity(city);
    }

    public List<Branch> getBranchesByState(String state) {
        return branchRepository.findByState(state);
    }

    public List<Branch> getBranchesByType(Branch.BranchType type) {
        return branchRepository.findByBranchType(type);
    }
}
