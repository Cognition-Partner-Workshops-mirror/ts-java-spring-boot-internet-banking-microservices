package com.petclinic.vet.mapper;

import com.petclinic.vet.dto.VetResponseDto;
import com.petclinic.vet.entity.Vet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class VetMapper {

    private final SpecialtyMapper specialtyMapper;

    public VetResponseDto toResponseDto(Vet entity) {
        return VetResponseDto.builder()
                .id(entity.getId())
                .firstName(entity.getFirstName())
                .lastName(entity.getLastName())
                .specialties(entity.getSpecialties().stream()
                        .map(specialtyMapper::toResponseDto)
                        .collect(Collectors.toList()))
                .build();
    }
}
