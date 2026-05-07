package com.petclinic.vet.service;

import com.petclinic.vet.dto.SpecialtyRequestDto;
import com.petclinic.vet.dto.SpecialtyResponseDto;
import com.petclinic.vet.dto.VetResponseDto;
import com.petclinic.vet.entity.Specialty;
import com.petclinic.vet.entity.Vet;
import com.petclinic.vet.mapper.SpecialtyMapper;
import com.petclinic.vet.mapper.VetMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MapperTest {

    private final SpecialtyMapper specialtyMapper = new SpecialtyMapper();
    private final VetMapper vetMapper = new VetMapper(specialtyMapper);

    @Test
    void specialtyMapper_toEntity() {
        SpecialtyRequestDto dto = SpecialtyRequestDto.builder().name("radiology").build();
        Specialty entity = specialtyMapper.toEntity(dto);
        assertThat(entity.getName()).isEqualTo("radiology");
        assertThat(entity.getId()).isNull();
    }

    @Test
    void specialtyMapper_toResponseDto() {
        Specialty entity = Specialty.builder().id(1).name("radiology")
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
        SpecialtyResponseDto dto = specialtyMapper.toResponseDto(entity);
        assertThat(dto.getId()).isEqualTo(1);
        assertThat(dto.getName()).isEqualTo("radiology");
    }

    @Test
    void specialtyMapper_updateEntity() {
        Specialty entity = Specialty.builder().id(1).name("radiology")
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
        SpecialtyRequestDto dto = SpecialtyRequestDto.builder().name("surgery").build();
        specialtyMapper.updateEntity(entity, dto);
        assertThat(entity.getName()).isEqualTo("surgery");
    }

    @Test
    void vetMapper_toResponseDto() {
        Specialty specialty = Specialty.builder().id(1).name("radiology")
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
        Vet vet = Vet.builder()
                .id(1)
                .firstName("James")
                .lastName("Carter")
                .specialties(Set.of(specialty))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        VetResponseDto dto = vetMapper.toResponseDto(vet);

        assertThat(dto.getId()).isEqualTo(1);
        assertThat(dto.getFirstName()).isEqualTo("James");
        assertThat(dto.getLastName()).isEqualTo("Carter");
        assertThat(dto.getSpecialties()).hasSize(1);
        assertThat(dto.getSpecialties().get(0).getName()).isEqualTo("radiology");
    }
}
