package com.javatodev.finance.repository;

import com.javatodev.finance.model.entity.MdmUserEntity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Repository for MdmUser entity CRUD operations.
 */
public interface MdmUserRepository extends JpaRepository<MdmUserEntity, Long> {
    Optional<MdmUserEntity> findByUsername(String username);
    Optional<MdmUserEntity> findByEmail(String email);
}
