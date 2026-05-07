package com.citi.banking.repository;

import com.citi.banking.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {
    Optional<Customer> findByEmail(String email);
    List<Customer> findBySegment(Customer.CustomerSegment segment);
    List<Customer> findByCityAndState(String city, String state);
}
