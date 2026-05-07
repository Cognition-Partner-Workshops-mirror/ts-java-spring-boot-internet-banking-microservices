package com.javatodev.finance.service;

import com.javatodev.finance.exception.EntityNotFoundException;
import com.javatodev.finance.exception.InvalidEmailException;
import com.javatodev.finance.exception.UserAlreadyRegisteredException;
import com.javatodev.finance.model.dto.Status;
import com.javatodev.finance.model.dto.User;
import com.javatodev.finance.model.dto.UserUpdateRequest;
import com.javatodev.finance.model.entity.UserEntity;
import com.javatodev.finance.model.repository.UserRepository;
import com.javatodev.finance.model.rest.response.UserResponse;
import com.javatodev.finance.service.rest.BankingCoreRestClient;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.representations.idm.UserRepresentation;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserServiceTest {

    private KeycloakUserService keycloakUserService;
    private UserRepository userRepository;
    private BankingCoreRestClient bankingCoreRestClient;
    private UserService userService;

    @BeforeEach
    void setUp() {
        keycloakUserService = mock(KeycloakUserService.class);
        userRepository = mock(UserRepository.class);
        bankingCoreRestClient = mock(BankingCoreRestClient.class);
        userService = new UserService(keycloakUserService, userRepository, bankingCoreRestClient);
    }

    @Test
    void createUser_success() {
        User user = new User();
        user.setEmail("test@bank.com");
        user.setIdentification("NIC001");
        user.setPassword("password123");

        when(keycloakUserService.readUserByEmail("test@bank.com")).thenReturn(Collections.emptyList());

        UserResponse coreUser = new UserResponse();
        coreUser.setId(1);
        coreUser.setEmail("test@bank.com");
        coreUser.setFirstName("John");
        coreUser.setLastName("Doe");
        coreUser.setIdentificationNumber("NIC001");
        when(bankingCoreRestClient.readUser("NIC001")).thenReturn(coreUser);

        when(keycloakUserService.createUser(any(UserRepresentation.class))).thenReturn(201);

        UserRepresentation keycloakUser = new UserRepresentation();
        keycloakUser.setId("kc-uuid-123");
        when(keycloakUserService.readUserByEmail("test@bank.com"))
            .thenReturn(Collections.emptyList())
            .thenReturn(List.of(keycloakUser));

        UserEntity savedEntity = new UserEntity();
        savedEntity.setId(1L);
        savedEntity.setAuthId("kc-uuid-123");
        savedEntity.setIdentification("NIC001");
        savedEntity.setStatus(Status.PENDING);
        when(userRepository.save(any(UserEntity.class))).thenReturn(savedEntity);

        User result = userService.createUser(user);

        assertNotNull(result);
        assertEquals("kc-uuid-123", result.getAuthId());
        assertEquals(Status.PENDING, result.getStatus());
    }

    @Test
    void createUser_emailAlreadyRegistered() {
        User user = new User();
        user.setEmail("existing@bank.com");
        user.setIdentification("NIC001");
        user.setPassword("password123");

        UserRepresentation existing = new UserRepresentation();
        existing.setEmail("existing@bank.com");
        when(keycloakUserService.readUserByEmail("existing@bank.com")).thenReturn(List.of(existing));

        assertThrows(UserAlreadyRegisteredException.class, () -> userService.createUser(user));
    }

    @Test
    void createUser_invalidEmail() {
        User user = new User();
        user.setEmail("wrong@bank.com");
        user.setIdentification("NIC001");
        user.setPassword("password123");

        when(keycloakUserService.readUserByEmail("wrong@bank.com")).thenReturn(Collections.emptyList());

        UserResponse coreUser = new UserResponse();
        coreUser.setId(1);
        coreUser.setEmail("correct@bank.com");
        when(bankingCoreRestClient.readUser("NIC001")).thenReturn(coreUser);

        assertThrows(InvalidEmailException.class, () -> userService.createUser(user));
    }

    @Test
    void readUser_success() {
        UserEntity entity = new UserEntity();
        entity.setId(1L);
        entity.setAuthId("kc-uuid-123");
        entity.setIdentification("NIC001");
        entity.setStatus(Status.APPROVED);
        when(userRepository.findById(1L)).thenReturn(Optional.of(entity));

        User result = userService.readUser(1L);

        assertNotNull(result);
        assertEquals(1L, result.getId());
    }

    @Test
    void readUser_notFound() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> userService.readUser(999L));
    }

    @Test
    void updateUser_approve() {
        UserEntity entity = new UserEntity();
        entity.setId(1L);
        entity.setAuthId("kc-uuid-123");
        entity.setStatus(Status.PENDING);
        when(userRepository.findById(1L)).thenReturn(Optional.of(entity));

        UserRepresentation keycloakUser = new UserRepresentation();
        keycloakUser.setId("kc-uuid-123");
        when(keycloakUserService.readUser("kc-uuid-123")).thenReturn(keycloakUser);

        UserEntity updatedEntity = new UserEntity();
        updatedEntity.setId(1L);
        updatedEntity.setAuthId("kc-uuid-123");
        updatedEntity.setStatus(Status.APPROVED);
        when(userRepository.save(any(UserEntity.class))).thenReturn(updatedEntity);

        UserUpdateRequest updateRequest = new UserUpdateRequest();
        updateRequest.setStatus(Status.APPROVED);

        User result = userService.updateUser(1L, updateRequest);

        assertEquals(Status.APPROVED, result.getStatus());
        verify(keycloakUserService).updateUser(any(UserRepresentation.class));
    }

    @Test
    void updateUser_notFound() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());
        UserUpdateRequest updateRequest = new UserUpdateRequest();
        updateRequest.setStatus(Status.APPROVED);
        assertThrows(EntityNotFoundException.class, () -> userService.updateUser(999L, updateRequest));
    }
}
