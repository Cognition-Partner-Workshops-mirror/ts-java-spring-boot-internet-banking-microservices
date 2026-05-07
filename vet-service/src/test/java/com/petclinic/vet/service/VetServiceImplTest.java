package com.petclinic.vet.service;

import com.petclinic.vet.dto.SpecialtyResponseDto;
import com.petclinic.vet.dto.VetRequestDto;
import com.petclinic.vet.dto.VetResponseDto;
import com.petclinic.vet.entity.Specialty;
import com.petclinic.vet.entity.Vet;
import com.petclinic.vet.exception.ResourceNotFoundException;
import com.petclinic.vet.mapper.VetMapper;
import com.petclinic.vet.repository.SpecialtyRepository;
import com.petclinic.vet.repository.VetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VetServiceImplTest {

    @Mock
    private VetRepository vetRepository;

    @Mock
    private SpecialtyRepository specialtyRepository;

    @Mock
    private VetMapper vetMapper;

    @InjectMocks
    private VetServiceImpl vetService;

    private Vet vet;
    private Specialty specialty;
    private VetResponseDto responseDto;
    private VetRequestDto requestDto;

    @BeforeEach
    void setUp() {
        specialty = Specialty.builder()
                .id(1)
                .name("radiology")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        vet = Vet.builder()
                .id(1)
                .firstName("James")
                .lastName("Carter")
                .specialties(new HashSet<>(Set.of(specialty)))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        responseDto = VetResponseDto.builder()
                .id(1)
                .firstName("James")
                .lastName("Carter")
                .specialties(List.of(SpecialtyResponseDto.builder().id(1).name("radiology").build()))
                .build();

        requestDto = VetRequestDto.builder()
                .firstName("James")
                .lastName("Carter")
                .specialtyIds(List.of(1))
                .build();
    }

    @Test
    void listVets_returnsAll() {
        when(vetRepository.findAll()).thenReturn(List.of(vet));
        when(vetMapper.toResponseDto(vet)).thenReturn(responseDto);

        List<VetResponseDto> result = vetService.listVets();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFirstName()).isEqualTo("James");
    }

    @Test
    void getVet_found() {
        when(vetRepository.findById(1)).thenReturn(Optional.of(vet));
        when(vetMapper.toResponseDto(vet)).thenReturn(responseDto);

        VetResponseDto result = vetService.getVet(1);

        assertThat(result.getId()).isEqualTo(1);
    }

    @Test
    void getVet_notFound() {
        when(vetRepository.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> vetService.getVet(99))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Vet not found with id: 99");
    }

    @Test
    void createVet_withSpecialties() {
        when(specialtyRepository.findAllById(List.of(1))).thenReturn(List.of(specialty));
        when(vetRepository.save(any(Vet.class))).thenReturn(vet);
        when(vetMapper.toResponseDto(vet)).thenReturn(responseDto);

        VetResponseDto result = vetService.createVet(requestDto);

        assertThat(result.getFirstName()).isEqualTo("James");
        verify(vetRepository).save(any(Vet.class));
    }

    @Test
    void createVet_withNullSpecialties() {
        VetRequestDto dtoNoSpecs = VetRequestDto.builder()
                .firstName("James")
                .lastName("Carter")
                .specialtyIds(null)
                .build();

        when(vetRepository.save(any(Vet.class))).thenReturn(vet);
        when(vetMapper.toResponseDto(vet)).thenReturn(responseDto);

        VetResponseDto result = vetService.createVet(dtoNoSpecs);

        assertThat(result).isNotNull();
    }

    @Test
    void createVet_withEmptySpecialties() {
        VetRequestDto dtoEmptySpecs = VetRequestDto.builder()
                .firstName("James")
                .lastName("Carter")
                .specialtyIds(List.of())
                .build();

        when(vetRepository.save(any(Vet.class))).thenReturn(vet);
        when(vetMapper.toResponseDto(vet)).thenReturn(responseDto);

        VetResponseDto result = vetService.createVet(dtoEmptySpecs);

        assertThat(result).isNotNull();
    }

    @Test
    void createVet_specialtyNotFound() {
        when(specialtyRepository.findAllById(List.of(99))).thenReturn(List.of());

        VetRequestDto dto = VetRequestDto.builder()
                .firstName("James")
                .lastName("Carter")
                .specialtyIds(List.of(99))
                .build();

        assertThatThrownBy(() -> vetService.createVet(dto))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Specialty not found with id: 99");
    }

    @Test
    void updateVet_found() {
        when(vetRepository.findById(1)).thenReturn(Optional.of(vet));
        when(specialtyRepository.findAllById(List.of(1))).thenReturn(List.of(specialty));
        when(vetRepository.save(vet)).thenReturn(vet);
        when(vetMapper.toResponseDto(vet)).thenReturn(responseDto);

        VetResponseDto result = vetService.updateVet(1, requestDto);

        assertThat(result.getFirstName()).isEqualTo("James");
    }

    @Test
    void updateVet_notFound() {
        when(vetRepository.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> vetService.updateVet(99, requestDto))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteVet_found() {
        when(vetRepository.findById(1)).thenReturn(Optional.of(vet));
        when(vetMapper.toResponseDto(vet)).thenReturn(responseDto);

        VetResponseDto result = vetService.deleteVet(1);

        assertThat(result.getId()).isEqualTo(1);
        verify(vetRepository).delete(vet);
    }

    @Test
    void deleteVet_notFound() {
        when(vetRepository.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> vetService.deleteVet(99))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void findBySpecialty_returnsResults() {
        when(vetRepository.findBySpecialtyId(1)).thenReturn(List.of(vet));
        when(vetMapper.toResponseDto(vet)).thenReturn(responseDto);

        List<VetResponseDto> result = vetService.findBySpecialty(1);

        assertThat(result).hasSize(1);
    }

    @Test
    void searchByName_returnsResults() {
        when(vetRepository.findByNameContaining("James")).thenReturn(List.of(vet));
        when(vetMapper.toResponseDto(vet)).thenReturn(responseDto);

        List<VetResponseDto> result = vetService.searchByName("James");

        assertThat(result).hasSize(1);
    }
}
