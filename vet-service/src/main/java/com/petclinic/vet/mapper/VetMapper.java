package com.petclinic.vet.mapper;

import com.petclinic.vet.dto.SpecialtyResponseDto;
import com.petclinic.vet.dto.VetRequestDto;
import com.petclinic.vet.dto.VetResponseDto;
import com.petclinic.vet.entity.Specialty;
import com.petclinic.vet.entity.Vet;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class VetMapper {

    public VetResponseDto toResponseDto(Vet vet) {
        List<SpecialtyResponseDto> specialtyDtos = vet.getSpecialties().stream()
                .map(this::toSpecialtyResponseDto)
                .sorted((a, b) -> a.getId().compareTo(b.getId()))
                .collect(Collectors.toList());

        return VetResponseDto.builder()
                .id(vet.getId())
                .firstName(vet.getFirstName())
                .lastName(vet.getLastName())
                .specialties(specialtyDtos)
                .build();
    }

    public Vet toEntity(VetRequestDto dto) {
        return Vet.builder()
                .firstName(dto.getFirstName())
                .lastName(dto.getLastName())
                .build();
    }

    public void updateEntity(Vet vet, VetRequestDto dto) {
        vet.setFirstName(dto.getFirstName());
        vet.setLastName(dto.getLastName());
    }

    public List<VetResponseDto> toResponseDtoList(List<Vet> vets) {
        return vets.stream()
                .map(this::toResponseDto)
                .collect(Collectors.toList());
    }

    private SpecialtyResponseDto toSpecialtyResponseDto(Specialty specialty) {
        return SpecialtyResponseDto.builder()
                .id(specialty.getId())
                .name(specialty.getName())
                .build();
    }
}
