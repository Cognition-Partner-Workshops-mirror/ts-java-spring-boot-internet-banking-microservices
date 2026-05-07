package com.javatodev.finance.model.mapper;

import com.javatodev.finance.model.dto.Member;
import com.javatodev.finance.model.entity.MemberEntity;

import org.springframework.beans.BeanUtils;

public class MemberMapper extends BaseMapper<MemberEntity, Member> {

    @Override
    public MemberEntity convertToEntity(Member dto, Object... args) {
        MemberEntity entity = new MemberEntity();
        if (dto != null) {
            BeanUtils.copyProperties(dto, entity);
        }
        return entity;
    }

    @Override
    public Member convertToDto(MemberEntity entity, Object... args) {
        Member dto = new Member();
        if (entity != null) {
            BeanUtils.copyProperties(entity, dto);
        }
        return dto;
    }
}
