package com.banking.oracle.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.banking.oracle.exception.CustomerNotFoundException;
import com.banking.oracle.exception.DuplicateEmailException;
import com.banking.oracle.model.Customer;
import com.banking.oracle.model.CustomerStatus;
import com.banking.oracle.model.dto.CustomerRequest;
import com.banking.oracle.model.dto.CustomerResponse;
import com.banking.oracle.repository.CustomerRepository;

import lombok.RequiredArgsConstructor;

/**
 * Service layer for Customer business logic.
 * Handles data transformation between DTOs and entities,
 * validation, and orchestrates repository operations.
 */
@Service
@RequiredArgsConstructor
public class CustomerService {

    private static final Logger log = LoggerFactory.getLogger(CustomerService.class);

    /* Injected via constructor by Lombok's @RequiredArgsConstructor */
    private final CustomerRepository customerRepository;

    /**
     * Creates a new customer in the Oracle database.
     * Validates that the email is not already in use before persisting.
     *
     * @param request the customer creation request DTO
     * @return CustomerResponse containing the created customer details
     * @throws DuplicateEmailException if the email already exists
     */
    @Transactional
    public CustomerResponse createCustomer(CustomerRequest request) {
        log.info("Creating new customer with email: {}", request.getEmail());

        /* Check for duplicate email before creating */
        if (customerRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateEmailException(request.getEmail());
        }

        /* Map request DTO to entity */
        Customer customer = Customer.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .phoneNumber(request.getPhoneNumber())
                .address(request.getAddress())
                .status(CustomerStatus.ACTIVE)
                .build();

        /* Persist to Oracle database */
        Customer savedCustomer = customerRepository.save(customer);
        log.info("Customer created successfully with id: {}", savedCustomer.getId());

        return mapToResponse(savedCustomer);
    }

    /**
     * Retrieves a customer by their unique ID.
     *
     * @param id the customer ID
     * @return CustomerResponse containing the customer details
     * @throws CustomerNotFoundException if no customer exists with the given ID
     */
    @Transactional(readOnly = true)
    public CustomerResponse getCustomerById(Long id) {
        log.debug("Fetching customer with id: {}", id);
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new CustomerNotFoundException(id));
        return mapToResponse(customer);
    }

    /**
     * Retrieves all customers from the Oracle database.
     *
     * @return list of all customer responses
     */
    @Transactional(readOnly = true)
    public List<CustomerResponse> getAllCustomers() {
        log.debug("Fetching all customers");
        return customerRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    /**
     * Retrieves customers filtered by their account status.
     *
     * @param status the customer status to filter by (ACTIVE, INACTIVE, SUSPENDED)
     * @return list of customers matching the given status
     */
    @Transactional(readOnly = true)
    public List<CustomerResponse> getCustomersByStatus(CustomerStatus status) {
        log.debug("Fetching customers with status: {}", status);
        return customerRepository.findByStatus(status)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    /**
     * Searches customers by name (case-insensitive partial match on first or last name).
     *
     * @param name the search term
     * @return list of customers matching the search criteria
     */
    @Transactional(readOnly = true)
    public List<CustomerResponse> searchCustomersByName(String name) {
        log.debug("Searching customers by name: {}", name);
        return customerRepository.searchByName(name)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    /**
     * Updates an existing customer's details in the Oracle database.
     * Only updates non-null fields from the request.
     *
     * @param id the ID of the customer to update
     * @param request the update request DTO with new values
     * @return CustomerResponse containing the updated customer details
     * @throws CustomerNotFoundException if no customer exists with the given ID
     * @throws DuplicateEmailException if the new email is already used by another customer
     */
    @Transactional
    public CustomerResponse updateCustomer(Long id, CustomerRequest request) {
        log.info("Updating customer with id: {}", id);

        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new CustomerNotFoundException(id));

        /* Check for email uniqueness if email is being changed */
        if (!customer.getEmail().equals(request.getEmail())
                && customerRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateEmailException(request.getEmail());
        }

        /* Update entity fields from request DTO */
        customer.setFirstName(request.getFirstName());
        customer.setLastName(request.getLastName());
        customer.setEmail(request.getEmail());
        customer.setPhoneNumber(request.getPhoneNumber());
        customer.setAddress(request.getAddress());

        Customer updatedCustomer = customerRepository.save(customer);
        log.info("Customer updated successfully with id: {}", updatedCustomer.getId());

        return mapToResponse(updatedCustomer);
    }

    /**
     * Deletes a customer from the Oracle database.
     *
     * @param id the ID of the customer to delete
     * @throws CustomerNotFoundException if no customer exists with the given ID
     */
    @Transactional
    public void deleteCustomer(Long id) {
        log.info("Deleting customer with id: {}", id);

        if (!customerRepository.existsById(id)) {
            throw new CustomerNotFoundException(id);
        }

        customerRepository.deleteById(id);
        log.info("Customer deleted successfully with id: {}", id);
    }

    /**
     * Maps a Customer JPA entity to a CustomerResponse DTO.
     * Decouples the internal persistence model from the API response.
     *
     * @param customer the JPA entity to convert
     * @return CustomerResponse DTO with the entity's data
     */
    private CustomerResponse mapToResponse(Customer customer) {
        return CustomerResponse.builder()
                .id(customer.getId())
                .firstName(customer.getFirstName())
                .lastName(customer.getLastName())
                .email(customer.getEmail())
                .phoneNumber(customer.getPhoneNumber())
                .address(customer.getAddress())
                .status(customer.getStatus())
                .createdAt(customer.getCreatedAt())
                .updatedAt(customer.getUpdatedAt())
                .build();
    }
}
