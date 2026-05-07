package com.petclinic.vet.mapper;

import com.petclinic.vet.dto.SpecialtyRequestDto;
import com.petclinic.vet.dto.SpecialtyResponseDto;
import com.petclinic.vet.entity.Specialty;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SpecialtyMapperTest {

    @Test
    void toEntity_mapsNameOnly() {
        SpecialtyRequestDto dto = new SpecialtyRequestDto("radiology");
        Specialty entity = SpecialtyMapper.toEntity(dto);
        assertThat(entity.getName()).isEqualTo("radiology");
        assertThat(entity.getId()).isNull();
    }

    @Test
    void toResponseDto_mapsAllFields() {
        Specialty entity = Specialty.builder()
                .id(1).name("radiology")
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .build();
        SpecialtyResponseDto dto = SpecialtyMapper.toResponseDto(entity);
        assertThat(dto.getId()).isEqualTo(1);
        assertThat(dto.getName()).isEqualTo("radiology");
    }

    @Test
    void toResponseDtoList_mapsAll() {
        Specialty s1 = Specialty.builder().id(1).name("radiology")
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
        Specialty s2 = Specialty.builder().id(2).name("surgery")
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
        List<SpecialtyResponseDto> result = SpecialtyMapper.toResponseDtoList(List.of(s1, s2));
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("radiology");
        assertThat(result.get(1).getName()).isEqualTo("surgery");
    }

    @Test
    void toResponseDtoList_emptyList() {
        List<SpecialtyResponseDto> result = SpecialtyMapper.toResponseDtoList(List.of());
        assertThat(result).isEmpty();
    }

    @Test
    void updateEntity_updatesName() {
        Specialty entity = Specialty.builder().id(1).name("old")
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
        SpecialtyMapper.updateEntity(entity, new SpecialtyRequestDto("new"));
        assertThat(entity.getName()).isEqualTo("new");
        assertThat(entity.getId()).isEqualTo(1);
    }
}
