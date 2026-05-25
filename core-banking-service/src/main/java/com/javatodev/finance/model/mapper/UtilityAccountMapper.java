package com.javatodev.finance.model.mapper;

import com.javatodev.finance.model.dto.UtilityAccount;
import com.javatodev.finance.model.entity.UtilityAccountEntity;

import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;

/**
 * Spring-managed bean mapper for UtilityAccount entity/DTO conversion (DIP - Phase 5).
 */
@Component
public class UtilityAccountMapper extends BaseMapper<UtilityAccountEntity, UtilityAccount> {
    @Override
    public UtilityAccountEntity convertToEntity(UtilityAccount dto, Object... args) {
        UtilityAccountEntity entity = new UtilityAccountEntity();
        if (dto != null) {
            BeanUtils.copyProperties(dto, entity);
        }
        return entity;
    }

    @Override
    public UtilityAccount convertToDto(UtilityAccountEntity entity, Object... args) {
        UtilityAccount dto = new UtilityAccount();
        if (entity != null) {
            BeanUtils.copyProperties(entity, dto);
        }
        return dto;
    }
}
