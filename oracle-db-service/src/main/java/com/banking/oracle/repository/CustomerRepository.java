package com.banking.oracle.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.banking.oracle.model.Customer;
import com.banking.oracle.model.CustomerStatus;

/**
 * Spring Data JPA repository for Customer entity.
 * Provides CRUD operations and custom query methods for the CUSTOMERS table.
 * Spring Data JPA auto-generates the implementation at runtime.
 */
@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    /**
     * Find a customer by their email address.
     *
     * @param email the customer's email
     * @return Optional containing the customer if found
     */
    Optional<Customer> findByEmail(String email);

    /**
     * Find all customers with a given status (ACTIVE, INACTIVE, SUSPENDED).
     *
     * @param status the customer status to filter by
     * @return list of customers matching the status
     */
    List<Customer> findByStatus(CustomerStatus status);

    /**
     * Search customers by first name or last name using case-insensitive partial match.
     * Uses JPQL LOWER function which maps to Oracle's LOWER() for case-insensitive search.
     *
     * @param name the search term to match against first or last name
     * @return list of customers matching the search criteria
     */
    @Query("SELECT c FROM Customer c WHERE LOWER(c.firstName) LIKE LOWER(CONCAT('%', :name, '%')) " +
           "OR LOWER(c.lastName) LIKE LOWER(CONCAT('%', :name, '%'))")
    List<Customer> searchByName(@Param("name") String name);

    /**
     * Check if a customer with the given email already exists.
     * Useful for validation before creating a new customer.
     *
     * @param email the email to check
     * @return true if a customer with this email exists
     */
    boolean existsByEmail(String email);
}
