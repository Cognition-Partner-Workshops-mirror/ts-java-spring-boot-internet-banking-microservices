package com.citi.banking.controller;

import com.citi.banking.model.Branch;
import com.citi.banking.service.BranchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/branches")
@RequiredArgsConstructor
@Tag(name = "Branches", description = "Citi branch location APIs")
public class BranchController {

    private final BranchService branchService;

    @GetMapping
    @Operation(summary = "Get all branches", description = "Returns paginated list of all Citi branches")
    public ResponseEntity<Page<Branch>> getAllBranches(Pageable pageable) {
        return ResponseEntity.ok(branchService.getAllBranches(pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get branch by ID")
    public ResponseEntity<Branch> getBranchById(@PathVariable Long id) {
        return ResponseEntity.ok(branchService.getBranchById(id));
    }

    @GetMapping("/code/{branchCode}")
    @Operation(summary = "Get branch by branch code")
    public ResponseEntity<Branch> getBranchByCode(@PathVariable String branchCode) {
        return ResponseEntity.ok(branchService.getBranchByCode(branchCode));
    }

    @GetMapping("/city/{city}")
    @Operation(summary = "Get branches by city")
    public ResponseEntity<List<Branch>> getBranchesByCity(@PathVariable String city) {
        return ResponseEntity.ok(branchService.getBranchesByCity(city));
    }

    @GetMapping("/state/{state}")
    @Operation(summary = "Get branches by state")
    public ResponseEntity<List<Branch>> getBranchesByState(@PathVariable String state) {
        return ResponseEntity.ok(branchService.getBranchesByState(state));
    }

    @GetMapping("/type/{type}")
    @Operation(summary = "Get branches by type", description = "Filter by FULL_SERVICE, EXPRESS, WEALTH_CENTER, PRIVATE_BANK, COMMERCIAL")
    public ResponseEntity<List<Branch>> getBranchesByType(@PathVariable Branch.BranchType type) {
        return ResponseEntity.ok(branchService.getBranchesByType(type));
    }
}
