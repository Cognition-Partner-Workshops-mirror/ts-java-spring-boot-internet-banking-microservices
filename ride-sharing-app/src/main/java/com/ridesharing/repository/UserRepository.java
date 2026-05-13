package com.ridesharing.repository;

import com.ridesharing.entity.User;
import com.ridesharing.enums.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for User entity operations.
 * Provides custom queries for finding users by email, phone, and role.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Boolean existsByEmail(String email);

    Boolean existsByPhoneNumber(String phoneNumber);

    List<User> findByRole(UserRole role);
}
