package com.petclinic.vet.mapper;

import com.petclinic.vet.dto.VetResponseDto;
import com.petclinic.vet.entity.Specialty;
import com.petclinic.vet.entity.Vet;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class VetMapperTest {

    @Test
    void toResponseDto_mapsAllFields() {
        Specialty radiology = Specialty.builder()
                .id(1).name("radiology")
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .build();
        Vet vet = Vet.builder()
                .id(1).firstName("James").lastName("Carter")
                .specialties(new HashSet<>(Set.of(radiology)))
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .build();

        VetResponseDto dto = VetMapper.toResponseDto(vet);

        assertThat(dto.getId()).isEqualTo(1);
        assertThat(dto.getFirstName()).isEqualTo("James");
        assertThat(dto.getLastName()).isEqualTo("Carter");
        assertThat(dto.getSpecialties()).hasSize(1);
        assertThat(dto.getSpecialties().get(0).getName()).isEqualTo("radiology");
    }

    @Test
    void toResponseDto_emptySpecialties() {
        Vet vet = Vet.builder()
                .id(2).firstName("Helen").lastName("Leary")
                .specialties(new HashSet<>())
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .build();

        VetResponseDto dto = VetMapper.toResponseDto(vet);

        assertThat(dto.getSpecialties()).isEmpty();
    }

    @Test
    void toResponseDtoList_mapsAll() {
        Vet v1 = Vet.builder().id(1).firstName("James").lastName("Carter")
                .specialties(new HashSet<>())
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
        Vet v2 = Vet.builder().id(2).firstName("Helen").lastName("Leary")
                .specialties(new HashSet<>())
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();

        List<VetResponseDto> result = VetMapper.toResponseDtoList(List.of(v1, v2));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getFirstName()).isEqualTo("James");
        assertThat(result.get(1).getFirstName()).isEqualTo("Helen");
    }

    @Test
    void toResponseDtoList_emptyList() {
        List<VetResponseDto> result = VetMapper.toResponseDtoList(List.of());
        assertThat(result).isEmpty();
    }
}
