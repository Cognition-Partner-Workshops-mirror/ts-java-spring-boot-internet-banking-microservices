package com.petclinic.vet.mapper;

import com.petclinic.vet.dto.SpecialtyRequestDto;
import com.petclinic.vet.dto.SpecialtyResponseDto;
import com.petclinic.vet.entity.Specialty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class SpecialtyMapper {

    public SpecialtyResponseDto toResponseDto(Specialty specialty) {
        return SpecialtyResponseDto.builder()
                .id(specialty.getId())
                .name(specialty.getName())
                .build();
    }

    public Specialty toEntity(SpecialtyRequestDto dto) {
        return Specialty.builder()
                .name(dto.getName())
                .build();
    }

    public void updateEntity(Specialty specialty, SpecialtyRequestDto dto) {
        specialty.setName(dto.getName());
    }

    public List<SpecialtyResponseDto> toResponseDtoList(List<Specialty> specialties) {
        return specialties.stream()
                .map(this::toResponseDto)
                .collect(Collectors.toList());
    }
}
