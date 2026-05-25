package com.javatodev.finance.service;

import com.javatodev.finance.exception.*;
import com.javatodev.finance.model.dto.CreateUserRequest;
import com.javatodev.finance.model.dto.Status;
import com.javatodev.finance.model.dto.User;
import com.javatodev.finance.model.dto.UserResponse;
import com.javatodev.finance.model.dto.UserUpdateRequest;
import com.javatodev.finance.model.entity.UserEntity;
import com.javatodev.finance.model.mapper.UserMapper;
import com.javatodev.finance.model.repository.UserRepository;
import com.javatodev.finance.service.rest.BankingCoreRestClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * User management service implementing IUserService.
 * Uses IKeycloakUserService interface (DIP), injected mapper (Phase 5),
 * CreateUserRequest/UserResponse DTOs (Phase 6), and KeycloakUserRepresentationFactory (Phase 10).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService implements IUserService {
    // Depends on interface, not concrete class (DIP)
    private final IKeycloakUserService keycloakUserService;
    private final UserRepository userRepository;
    private final BankingCoreRestClient bankingCoreRestClient;

    // Injected as Spring bean instead of manual instantiation (DIP - Phase 5)
    private final UserMapper userMapper;

    /**
     * Creates a user from the new CreateUserRequest DTO and returns UserResponse (Phase 6).
     * Delegates Keycloak UserRepresentation construction to KeycloakUserRepresentationFactory (Phase 10).
     */
    @Override
    public UserResponse createUser(CreateUserRequest request) {
        // Delegate to the existing logic via a User DTO for backward compatibility
        User user = new User();
        user.setEmail(request.getEmail());
        user.setIdentification(request.getIdentification());
        user.setPassword(request.getPassword());

        User created = createUser(user);

        // Convert to UserResponse DTO (excludes password)
        return UserResponse.builder()
            .id(created.getId())
            .email(created.getEmail())
            .identification(created.getIdentification())
            .status(created.getStatus())
            .authId(created.getAuthId())
            .build();
    }

    /**
     * Legacy createUser accepting User DTO.
     * Uses KeycloakUserRepresentationFactory for Keycloak user construction (Phase 10).
     */
    @Override
    public User createUser(User user) {

        List<UserRepresentation> userRepresentations = keycloakUserService.readUserByEmail(user.getEmail());
        if (!userRepresentations.isEmpty()) {
            throw new UserAlreadyRegisteredException("This email already registered as a user. Please check and retry.", GlobalErrorCode.ERROR_EMAIL_REGISTERED);
        }

        com.javatodev.finance.model.rest.response.UserResponse userResponse = bankingCoreRestClient.readUser(user.getIdentification());

        if (userResponse.getId() != null) {

            if (!userResponse.getEmail().equals(user.getEmail())) {
                throw new InvalidEmailException("Incorrect email. Please check and retry.", GlobalErrorCode.ERROR_INVALID_EMAIL);
            }

            // Use factory to build Keycloak UserRepresentation (Phase 10: Builder Pattern)
            UserRepresentation userRepresentation = KeycloakUserRepresentationFactory.createNewUser(
                userResponse.getEmail(),
                userResponse.getFirstName(),
                userResponse.getLastName(),
                user.getPassword());

            Integer userCreationResponse = keycloakUserService.createUser(userRepresentation);

            if (userCreationResponse == 201) {
                log.info("User created under given username {}", user.getEmail());

                List<UserRepresentation> userRepresentations1 = keycloakUserService.readUserByEmail(user.getEmail());
                user.setAuthId(userRepresentations1.get(0).getId());
                user.setStatus(Status.PENDING);
                user.setIdentification(userResponse.getIdentificationNumber());
                UserEntity save = userRepository.save(userMapper.convertToEntity(user));
                return userMapper.convertToDto(save);
            }

        }

        throw new InvalidBankingUserException("We couldn't find user under given identification. Please check and retry", GlobalErrorCode.ERROR_USER_NOT_FOUND_UNDER_NIC);

    }

    @Override
    public List<User> readUsers(Pageable pageable) {
        Page<UserEntity> allUsersInDb = userRepository.findAll(pageable);
        List<User> users = userMapper.convertToDtoList(allUsersInDb.getContent());
        users.forEach(user -> {
            UserRepresentation userRepresentation = keycloakUserService.readUser(user.getAuthId());
            user.setId(user.getId());
            user.setEmail(userRepresentation.getEmail());
            user.setIdentification(user.getIdentification());
        });
        return users;
    }

    @Override
    public User readUser(Long userId) {
        return userMapper.convertToDto(userRepository.findById(userId).orElseThrow(EntityNotFoundException::new));
    }

    @Override
    public User updateUser(Long id, UserUpdateRequest userUpdateRequest) {
        UserEntity userEntity = userRepository.findById(id).orElseThrow(EntityNotFoundException::new);

        if (userUpdateRequest.getStatus() == Status.APPROVED) {
            UserRepresentation userRepresentation = keycloakUserService.readUser(userEntity.getAuthId());
            userRepresentation.setEnabled(true);
            userRepresentation.setEmailVerified(true);
            keycloakUserService.updateUser(userRepresentation);
        }

        userEntity.setStatus(userUpdateRequest.getStatus());
        return userMapper.convertToDto(userRepository.save(userEntity));
    }
}
