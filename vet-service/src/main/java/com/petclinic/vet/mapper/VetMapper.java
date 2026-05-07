package com.petclinic.vet.mapper;

import com.petclinic.vet.dto.VetResponseDto;
import com.petclinic.vet.entity.Vet;

import java.util.List;

public final class VetMapper {

    private VetMapper() {
    }

    public static VetResponseDto toResponseDto(Vet entity) {
        return VetResponseDto.builder()
                .id(entity.getId())
                .firstName(entity.getFirstName())
                .lastName(entity.getLastName())
                .specialties(
                        entity.getSpecialties().stream()
                                .map(SpecialtyMapper::toResponseDto)
                                .toList()
                )
                .build();
    }

    public static List<VetResponseDto> toResponseDtoList(List<Vet> entities) {
        return entities.stream()
                .map(VetMapper::toResponseDto)
                .toList();
    }
}
