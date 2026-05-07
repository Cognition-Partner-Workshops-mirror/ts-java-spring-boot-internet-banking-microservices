package com.petclinic.vet.service;

import com.petclinic.vet.dto.SpecialtyRequestDto;
import com.petclinic.vet.dto.SpecialtyResponseDto;
import com.petclinic.vet.entity.Specialty;
import com.petclinic.vet.exception.ResourceNotFoundException;
import com.petclinic.vet.repository.SpecialtyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpecialtyServiceTest {

    @Mock
    private SpecialtyRepository specialtyRepository;

    @InjectMocks
    private SpecialtyServiceImpl specialtyService;

    private Specialty radiology;
    private Specialty surgery;

    @BeforeEach
    void setUp() {
        radiology = Specialty.builder()
                .id(1)
                .name("radiology")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        surgery = Specialty.builder()
                .id(2)
                .name("surgery")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    void listSpecialties_returnsAll() {
        when(specialtyRepository.findAll()).thenReturn(List.of(radiology, surgery));

        List<SpecialtyResponseDto> result = specialtyService.listSpecialties();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("radiology");
        assertThat(result.get(1).getName()).isEqualTo("surgery");
    }

    @Test
    void listSpecialties_emptyList() {
        when(specialtyRepository.findAll()).thenReturn(List.of());

        List<SpecialtyResponseDto> result = specialtyService.listSpecialties();

        assertThat(result).isEmpty();
    }

    @Test
    void getSpecialty_found() {
        when(specialtyRepository.findById(1)).thenReturn(Optional.of(radiology));

        SpecialtyResponseDto result = specialtyService.getSpecialty(1);

        assertThat(result.getId()).isEqualTo(1);
        assertThat(result.getName()).isEqualTo("radiology");
    }

    @Test
    void getSpecialty_notFound() {
        when(specialtyRepository.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> specialtyService.getSpecialty(99))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Specialty not found with id: 99");
    }

    @Test
    void addSpecialty_success() {
        SpecialtyRequestDto dto = new SpecialtyRequestDto("dentistry");
        Specialty saved = Specialty.builder().id(3).name("dentistry")
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
        when(specialtyRepository.save(any(Specialty.class))).thenReturn(saved);

        SpecialtyResponseDto result = specialtyService.addSpecialty(dto);

        assertThat(result.getId()).isEqualTo(3);
        assertThat(result.getName()).isEqualTo("dentistry");
    }

    @Test
    void updateSpecialty_success() {
        when(specialtyRepository.findById(1)).thenReturn(Optional.of(radiology));
        when(specialtyRepository.save(any(Specialty.class))).thenAnswer(inv -> inv.getArgument(0));

        SpecialtyRequestDto dto = new SpecialtyRequestDto("updated-radiology");
        SpecialtyResponseDto result = specialtyService.updateSpecialty(1, dto);

        assertThat(result.getName()).isEqualTo("updated-radiology");
    }

    @Test
    void updateSpecialty_notFound() {
        when(specialtyRepository.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> specialtyService.updateSpecialty(99, new SpecialtyRequestDto("x")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteSpecialty_success() {
        when(specialtyRepository.findById(1)).thenReturn(Optional.of(radiology));

        SpecialtyResponseDto result = specialtyService.deleteSpecialty(1);

        assertThat(result.getId()).isEqualTo(1);
        verify(specialtyRepository).delete(radiology);
    }

    @Test
    void deleteSpecialty_notFound() {
        when(specialtyRepository.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> specialtyService.deleteSpecialty(99))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void searchByName_returnsMatching() {
        when(specialtyRepository.findByNameContainingIgnoreCase("rad"))
                .thenReturn(List.of(radiology));

        List<SpecialtyResponseDto> result = specialtyService.searchByName("rad");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("radiology");
    }
}
