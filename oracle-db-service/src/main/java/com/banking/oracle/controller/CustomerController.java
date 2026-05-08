package com.banking.oracle.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.banking.oracle.model.CustomerStatus;
import com.banking.oracle.model.dto.CustomerRequest;
import com.banking.oracle.model.dto.CustomerResponse;
import com.banking.oracle.service.CustomerService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * REST Controller for Customer CRUD operations.
 * All endpoints are prefixed with /api/customers.
 * Supports create, read, update, delete, and search operations
 * backed by Oracle Database via Spring Data JPA.
 */
@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
@Tag(name = "Customer", description = "Customer management APIs connected to Oracle Database")
public class CustomerController {

    /* Injected via constructor by Lombok's @RequiredArgsConstructor */
    private final CustomerService customerService;

    /**
     * POST /api/customers - Create a new customer.
     * Validates the request body and persists the customer to Oracle DB.
     *
     * @param request the customer creation request with validated fields
     * @return ResponseEntity with created customer and 201 CREATED status
     */
    @PostMapping
    @Operation(summary = "Create a new customer", description = "Creates a new customer record in Oracle Database")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Customer created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request body"),
            @ApiResponse(responseCode = "409", description = "Customer with this email already exists")
    })
    public ResponseEntity<CustomerResponse> createCustomer(
            @Valid @RequestBody CustomerRequest request) {
        CustomerResponse response = customerService.createCustomer(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    /**
     * GET /api/customers/{id} - Retrieve a customer by ID.
     *
     * @param id the unique customer identifier
     * @return ResponseEntity with the customer details and 200 OK status
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get customer by ID", description = "Retrieves a customer record from Oracle Database by ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Customer found"),
            @ApiResponse(responseCode = "404", description = "Customer not found")
    })
    public ResponseEntity<CustomerResponse> getCustomerById(
            @Parameter(description = "Customer ID") @PathVariable Long id) {
        CustomerResponse response = customerService.getCustomerById(id);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/customers - Retrieve all customers.
     *
     * @return ResponseEntity with list of all customers and 200 OK status
     */
    @GetMapping
    @Operation(summary = "Get all customers", description = "Retrieves all customer records from Oracle Database")
    @ApiResponse(responseCode = "200", description = "List of all customers")
    public ResponseEntity<List<CustomerResponse>> getAllCustomers() {
        List<CustomerResponse> responses = customerService.getAllCustomers();
        return ResponseEntity.ok(responses);
    }

    /**
     * GET /api/customers/status/{status} - Retrieve customers filtered by status.
     *
     * @param status the customer status to filter by (ACTIVE, INACTIVE, SUSPENDED)
     * @return ResponseEntity with list of matching customers and 200 OK status
     */
    @GetMapping("/status/{status}")
    @Operation(summary = "Get customers by status", description = "Retrieves customers filtered by account status")
    @ApiResponse(responseCode = "200", description = "List of customers with the given status")
    public ResponseEntity<List<CustomerResponse>> getCustomersByStatus(
            @Parameter(description = "Customer status: ACTIVE, INACTIVE, SUSPENDED")
            @PathVariable CustomerStatus status) {
        List<CustomerResponse> responses = customerService.getCustomersByStatus(status);
        return ResponseEntity.ok(responses);
    }

    /**
     * GET /api/customers/search?name={name} - Search customers by name.
     * Performs case-insensitive partial matching on first and last names.
     *
     * @param name the search term to match against customer names
     * @return ResponseEntity with list of matching customers and 200 OK status
     */
    @GetMapping("/search")
    @Operation(summary = "Search customers by name", description = "Search customers by first or last name (case-insensitive)")
    @ApiResponse(responseCode = "200", description = "List of customers matching the search criteria")
    public ResponseEntity<List<CustomerResponse>> searchCustomers(
            @Parameter(description = "Name to search for") @RequestParam String name) {
        List<CustomerResponse> responses = customerService.searchCustomersByName(name);
        return ResponseEntity.ok(responses);
    }

    /**
     * PUT /api/customers/{id} - Update an existing customer.
     * Validates the request body and updates the customer in Oracle DB.
     *
     * @param id the ID of the customer to update
     * @param request the customer update request with validated fields
     * @return ResponseEntity with updated customer details and 200 OK status
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update customer", description = "Updates an existing customer record in Oracle Database")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Customer updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request body"),
            @ApiResponse(responseCode = "404", description = "Customer not found"),
            @ApiResponse(responseCode = "409", description = "Email already in use by another customer")
    })
    public ResponseEntity<CustomerResponse> updateCustomer(
            @Parameter(description = "Customer ID") @PathVariable Long id,
            @Valid @RequestBody CustomerRequest request) {
        CustomerResponse response = customerService.updateCustomer(id, request);
        return ResponseEntity.ok(response);
    }

    /**
     * DELETE /api/customers/{id} - Delete a customer by ID.
     *
     * @param id the ID of the customer to delete
     * @return ResponseEntity with 204 NO CONTENT status on success
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete customer", description = "Deletes a customer record from Oracle Database")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Customer deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Customer not found")
    })
    public ResponseEntity<Void> deleteCustomer(
            @Parameter(description = "Customer ID") @PathVariable Long id) {
        customerService.deleteCustomer(id);
        return ResponseEntity.noContent().build();
    }
}
