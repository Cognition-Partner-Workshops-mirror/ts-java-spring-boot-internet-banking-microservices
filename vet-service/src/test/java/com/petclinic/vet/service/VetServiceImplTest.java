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

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
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
    private VetResponseDto vetResponseDto;
    private VetRequestDto vetRequestDto;

    @BeforeEach
    void setUp() {
        specialty = Specialty.builder()
                .id(1)
                .name("radiology")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        vet = Vet.builder()
                .id(1)
                .firstName("James")
                .lastName("Carter")
                .specialties(new HashSet<>(Set.of(specialty)))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        SpecialtyResponseDto specialtyDto = SpecialtyResponseDto.builder()
                .id(1)
                .name("radiology")
                .build();

        vetResponseDto = VetResponseDto.builder()
                .id(1)
                .firstName("James")
                .lastName("Carter")
                .specialties(List.of(specialtyDto))
                .build();

        vetRequestDto = VetRequestDto.builder()
                .firstName("James")
                .lastName("Carter")
                .specialties(List.of(specialtyDto))
                .build();
    }

    @Test
    void getAllVets_shouldReturnAllVets() {
        when(vetRepository.findAll()).thenReturn(Arrays.asList(vet));
        when(vetMapper.toResponseDtoList(anyList())).thenReturn(Arrays.asList(vetResponseDto));

        List<VetResponseDto> result = vetService.getAllVets();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFirstName()).isEqualTo("James");
    }

    @Test
    void getVetById_shouldReturnVet() {
        when(vetRepository.findById(1)).thenReturn(Optional.of(vet));
        when(vetMapper.toResponseDto(vet)).thenReturn(vetResponseDto);

        VetResponseDto result = vetService.getVetById(1);

        assertThat(result.getId()).isEqualTo(1);
        assertThat(result.getFirstName()).isEqualTo("James");
        assertThat(result.getLastName()).isEqualTo("Carter");
    }

    @Test
    void getVetById_shouldThrowWhenNotFound() {
        when(vetRepository.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> vetService.getVetById(99))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Vet not found with id: 99");
    }

    @Test
    void createVet_shouldCreateAndReturn() {
        when(vetMapper.toEntity(vetRequestDto)).thenReturn(vet);
        when(specialtyRepository.findById(1)).thenReturn(Optional.of(specialty));
        when(vetRepository.save(any(Vet.class))).thenReturn(vet);
        when(vetMapper.toResponseDto(vet)).thenReturn(vetResponseDto);

        VetResponseDto result = vetService.createVet(vetRequestDto);

        assertThat(result.getFirstName()).isEqualTo("James");
        assertThat(result.getSpecialties()).hasSize(1);
        verify(vetRepository).save(any(Vet.class));
    }

    @Test
    void createVet_shouldThrowWhenSpecialtyNotFound() {
        when(vetMapper.toEntity(vetRequestDto)).thenReturn(vet);
        when(specialtyRepository.findById(1)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> vetService.createVet(vetRequestDto))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Specialty not found with id: 1");
    }

    @Test
    void createVet_shouldHandleEmptySpecialties() {
        VetRequestDto emptySpecialtiesRequest = VetRequestDto.builder()
                .firstName("James")
                .lastName("Carter")
                .specialties(Collections.emptyList())
                .build();
        Vet newVet = Vet.builder()
                .id(2)
                .firstName("James")
                .lastName("Carter")
                .specialties(new HashSet<>())
                .build();
        VetResponseDto emptyResponse = VetResponseDto.builder()
                .id(2)
                .firstName("James")
                .lastName("Carter")
                .specialties(Collections.emptyList())
                .build();

        when(vetMapper.toEntity(emptySpecialtiesRequest)).thenReturn(newVet);
        when(vetRepository.save(any(Vet.class))).thenReturn(newVet);
        when(vetMapper.toResponseDto(newVet)).thenReturn(emptyResponse);

        VetResponseDto result = vetService.createVet(emptySpecialtiesRequest);

        assertThat(result.getSpecialties()).isEmpty();
    }

    @Test
    void createVet_shouldHandleNullSpecialties() {
        VetRequestDto nullSpecialtiesRequest = VetRequestDto.builder()
                .firstName("James")
                .lastName("Carter")
                .specialties(null)
                .build();
        Vet newVet = Vet.builder()
                .id(3)
                .firstName("James")
                .lastName("Carter")
                .specialties(new HashSet<>())
                .build();
        VetResponseDto emptyResponse = VetResponseDto.builder()
                .id(3)
                .firstName("James")
                .lastName("Carter")
                .specialties(Collections.emptyList())
                .build();

        when(vetMapper.toEntity(nullSpecialtiesRequest)).thenReturn(newVet);
        when(vetRepository.save(any(Vet.class))).thenReturn(newVet);
        when(vetMapper.toResponseDto(newVet)).thenReturn(emptyResponse);

        VetResponseDto result = vetService.createVet(nullSpecialtiesRequest);

        assertThat(result.getSpecialties()).isEmpty();
    }

    @Test
    void updateVet_shouldUpdateAndReturn() {
        when(vetRepository.findById(1)).thenReturn(Optional.of(vet));
        when(specialtyRepository.findById(1)).thenReturn(Optional.of(specialty));
        when(vetRepository.save(any(Vet.class))).thenReturn(vet);
        when(vetMapper.toResponseDto(vet)).thenReturn(vetResponseDto);

        VetResponseDto result = vetService.updateVet(1, vetRequestDto);

        assertThat(result.getFirstName()).isEqualTo("James");
        verify(vetMapper).updateEntity(vet, vetRequestDto);
        verify(vetRepository).save(vet);
    }

    @Test
    void updateVet_shouldThrowWhenNotFound() {
        when(vetRepository.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> vetService.updateVet(99, vetRequestDto))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Vet not found with id: 99");
    }

    @Test
    void deleteVet_shouldDeleteAndReturn() {
        when(vetRepository.findById(1)).thenReturn(Optional.of(vet));
        when(vetMapper.toResponseDto(vet)).thenReturn(vetResponseDto);

        VetResponseDto result = vetService.deleteVet(1);

        assertThat(result.getId()).isEqualTo(1);
        verify(vetRepository).delete(vet);
    }

    @Test
    void deleteVet_shouldThrowWhenNotFound() {
        when(vetRepository.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> vetService.deleteVet(99))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Vet not found with id: 99");
    }

    @Test
    void findByLastName_shouldReturnMatchingVets() {
        when(vetRepository.findByLastNameContainingIgnoreCase("Carter")).thenReturn(Arrays.asList(vet));
        when(vetMapper.toResponseDtoList(anyList())).thenReturn(Arrays.asList(vetResponseDto));

        List<VetResponseDto> result = vetService.findByLastName("Carter");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getLastName()).isEqualTo("Carter");
    }

    @Test
    void findBySpecialtyName_shouldReturnMatchingVets() {
        when(vetRepository.findBySpecialtyName("radiology")).thenReturn(Arrays.asList(vet));
        when(vetMapper.toResponseDtoList(anyList())).thenReturn(Arrays.asList(vetResponseDto));

        List<VetResponseDto> result = vetService.findBySpecialtyName("radiology");

        assertThat(result).hasSize(1);
    }

    @Test
    void searchByName_shouldReturnMatchingVets() {
        when(vetRepository.findByNameContaining("James")).thenReturn(Arrays.asList(vet));
        when(vetMapper.toResponseDtoList(anyList())).thenReturn(Arrays.asList(vetResponseDto));

        List<VetResponseDto> result = vetService.searchByName("James");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFirstName()).isEqualTo("James");
    }
}
