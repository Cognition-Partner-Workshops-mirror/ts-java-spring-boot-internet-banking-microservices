package com.javatodev.finance.service;

import com.javatodev.finance.model.dto.CreateUserRequest;
import com.javatodev.finance.model.dto.User;
import com.javatodev.finance.model.dto.UserResponse;
import com.javatodev.finance.model.dto.UserUpdateRequest;

import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Interface for user management operations (OCP + DIP).
 * Uses separate request/response DTOs for create operations (SRP - Phase 6).
 */
public interface IUserService {
    // Accepts CreateUserRequest and returns UserResponse (Phase 6: separate DTOs)
    UserResponse createUser(CreateUserRequest request);

    // Kept legacy method for backward compatibility
    User createUser(User user);

    List<User> readUsers(Pageable pageable);
    User readUser(Long userId);
    User updateUser(Long id, UserUpdateRequest userUpdateRequest);
}
