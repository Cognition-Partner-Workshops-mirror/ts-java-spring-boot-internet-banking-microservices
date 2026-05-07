package com.petclinic.vet.mapper;

import com.petclinic.vet.dto.SpecialtyRequestDto;
import com.petclinic.vet.dto.SpecialtyResponseDto;
import com.petclinic.vet.entity.Specialty;
import org.springframework.stereotype.Component;

@Component
public class SpecialtyMapper {

    public Specialty toEntity(SpecialtyRequestDto dto) {
        return Specialty.builder()
                .name(dto.getName())
                .build();
    }

    public SpecialtyResponseDto toResponseDto(Specialty entity) {
        return SpecialtyResponseDto.builder()
                .id(entity.getId())
                .name(entity.getName())
                .build();
    }

    public void updateEntity(Specialty entity, SpecialtyRequestDto dto) {
        entity.setName(dto.getName());
    }
}
