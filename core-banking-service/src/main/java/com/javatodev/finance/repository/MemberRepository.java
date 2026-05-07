package com.javatodev.finance.repository;

import com.javatodev.finance.model.entity.MemberEntity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<MemberEntity, Long> {
    Optional<MemberEntity> findByIdentificationNumber(String identificationNumber);
    Optional<MemberEntity> findByEmail(String email);
}
