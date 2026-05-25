package com.javatodev.finance.service;

import com.javatodev.finance.model.dto.User;

import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Interface for core user operations (OCP + DIP).
 * Controllers depend on this interface, not the concrete class.
 */
public interface IUserService {
    User readUser(String identification);
    List<User> readUsers(Pageable pageable);
}
