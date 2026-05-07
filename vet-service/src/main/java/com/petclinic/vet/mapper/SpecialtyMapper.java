package com.petclinic.vet.mapper;

import com.petclinic.vet.dto.SpecialtyRequestDto;
import com.petclinic.vet.dto.SpecialtyResponseDto;
import com.petclinic.vet.entity.Specialty;

import java.util.List;

public final class SpecialtyMapper {

    private SpecialtyMapper() {
    }

    public static Specialty toEntity(SpecialtyRequestDto dto) {
        return Specialty.builder()
                .name(dto.getName())
                .build();
    }

    public static SpecialtyResponseDto toResponseDto(Specialty entity) {
        return SpecialtyResponseDto.builder()
                .id(entity.getId())
                .name(entity.getName())
                .build();
    }

    public static List<SpecialtyResponseDto> toResponseDtoList(List<Specialty> entities) {
        return entities.stream()
                .map(SpecialtyMapper::toResponseDto)
                .toList();
    }

    public static void updateEntity(Specialty entity, SpecialtyRequestDto dto) {
        entity.setName(dto.getName());
    }
}
