package com.javatodev.finance.model.mapper;

import com.javatodev.finance.model.dto.User;
import com.javatodev.finance.model.entity.UserEntity;

import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Spring-managed bean mapper for User entity/DTO conversion (DIP - Phase 5).
 */
@Component
@RequiredArgsConstructor
public class UserMapper extends BaseMapper<UserEntity, User> {
    // Injected as Spring bean instead of manual instantiation
    private final BankAccountMapper bankAccountMapper;

    @Override
    public UserEntity convertToEntity(User dto, Object... args) {
        UserEntity entity = new UserEntity();
        if (dto != null) {
            BeanUtils.copyProperties(dto, entity, "accounts");
            entity.setAccounts(bankAccountMapper.convertToEntityList(dto.getBankAccounts()));
        }
        return entity;
    }

    @Override
    public User convertToDto(UserEntity entity, Object... args) {
        User dto = new User();
        if (entity != null) {
            BeanUtils.copyProperties(entity, dto, "accounts");
            dto.setBankAccounts(bankAccountMapper.convertToDtoList(entity.getAccounts()));
        }
        return dto;
    }
}
