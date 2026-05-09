package com.javatodev.finance.service;

import com.javatodev.finance.exception.EntityNotFoundException;
import com.javatodev.finance.model.dto.request.MdmUserRequest;
import com.javatodev.finance.model.dto.response.MdmUserResponse;
import com.javatodev.finance.model.entity.MdmUserEntity;
import com.javatodev.finance.model.enums.MdmUserRole;
import com.javatodev.finance.model.enums.MdmUserStatus;
import com.javatodev.finance.repository.MdmUserRepository;

import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service handling MDM user management.
 * Users are assigned roles (ADMIN, DATA_STEWARD, VIEWER) for access control.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class MdmUserService {

    private final MdmUserRepository mdmUserRepository;
    private final PasswordEncoder passwordEncoder;

    /** Create a new MDM user with a hashed password. */
    @Transactional
    public MdmUserResponse createUser(MdmUserRequest request) {
        log.info("Creating MDM user: {}", request.getUsername());

        MdmUserEntity entity = new MdmUserEntity();
        entity.setUsername(request.getUsername());
        entity.setEmail(request.getEmail());
        entity.setPassword(passwordEncoder.encode(request.getPassword()));
        entity.setRole(MdmUserRole.valueOf(request.getRole()));
        entity.setStatus(MdmUserStatus.ACTIVE);

        MdmUserEntity saved = mdmUserRepository.save(entity);
        return toResponse(saved);
    }

    /** List all MDM users with pagination. */
    public List<MdmUserResponse> listUsers(Pageable pageable) {
        return mdmUserRepository.findAll(pageable).getContent()
            .stream().map(this::toResponse).collect(Collectors.toList());
    }

    /** Get a single MDM user by ID. */
    public MdmUserResponse getUser(Long id) {
        MdmUserEntity entity = mdmUserRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("MDM User not found with id: " + id));
        return toResponse(entity);
    }

    /** Update MDM user details (username, email, role). */
    @Transactional
    public MdmUserResponse updateUser(Long id, MdmUserRequest request) {
        log.info("Updating MDM user id: {}", id);
        MdmUserEntity entity = mdmUserRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("MDM User not found with id: " + id));

        entity.setUsername(request.getUsername());
        entity.setEmail(request.getEmail());
        if (request.getPassword() != null && !request.getPassword().isEmpty()) {
            entity.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        entity.setRole(MdmUserRole.valueOf(request.getRole()));

        MdmUserEntity saved = mdmUserRepository.save(entity);
        return toResponse(saved);
    }

    /** Deactivate an MDM user (soft delete). */
    @Transactional
    public MdmUserResponse deactivateUser(Long id) {
        log.info("Deactivating MDM user id: {}", id);
        MdmUserEntity entity = mdmUserRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("MDM User not found with id: " + id));

        entity.setStatus(MdmUserStatus.INACTIVE);
        MdmUserEntity saved = mdmUserRepository.save(entity);
        return toResponse(saved);
    }

    /** Convert entity to response DTO. Password is never included. */
    private MdmUserResponse toResponse(MdmUserEntity entity) {
        MdmUserResponse response = new MdmUserResponse();
        response.setId(entity.getId());
        response.setUsername(entity.getUsername());
        response.setEmail(entity.getEmail());
        response.setRole(entity.getRole().name());
        response.setStatus(entity.getStatus().name());
        return response;
    }
}
