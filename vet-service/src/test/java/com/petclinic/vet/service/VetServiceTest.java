package com.petclinic.vet.service;

import com.petclinic.vet.dto.VetRequestDto;
import com.petclinic.vet.dto.VetResponseDto;
import com.petclinic.vet.entity.Specialty;
import com.petclinic.vet.entity.Vet;
import com.petclinic.vet.exception.ResourceNotFoundException;
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
class VetServiceTest {

    @Mock
    private VetRepository vetRepository;

    @Mock
    private SpecialtyRepository specialtyRepository;

    @InjectMocks
    private VetServiceImpl vetService;

    private Specialty radiology;
    private Vet james;
    private Vet helen;

    @BeforeEach
    void setUp() {
        radiology = Specialty.builder()
                .id(1).name("radiology")
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .build();
        james = Vet.builder()
                .id(1).firstName("James").lastName("Carter")
                .specialties(new HashSet<>(Set.of(radiology)))
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .build();
        helen = Vet.builder()
                .id(2).firstName("Helen").lastName("Leary")
                .specialties(new HashSet<>())
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .build();
    }

    @Test
    void listVets_returnsAll() {
        when(vetRepository.findAll()).thenReturn(List.of(james, helen));

        List<VetResponseDto> result = vetService.listVets();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getFirstName()).isEqualTo("James");
    }

    @Test
    void listVets_emptyList() {
        when(vetRepository.findAll()).thenReturn(List.of());

        List<VetResponseDto> result = vetService.listVets();

        assertThat(result).isEmpty();
    }

    @Test
    void getVet_found() {
        when(vetRepository.findById(1)).thenReturn(Optional.of(james));

        VetResponseDto result = vetService.getVet(1);

        assertThat(result.getId()).isEqualTo(1);
        assertThat(result.getFirstName()).isEqualTo("James");
        assertThat(result.getSpecialties()).hasSize(1);
    }

    @Test
    void getVet_notFound() {
        when(vetRepository.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> vetService.getVet(99))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Vet not found with id: 99");
    }

    @Test
    void addVet_withSpecialties() {
        VetRequestDto dto = VetRequestDto.builder()
                .firstName("Linda").lastName("Douglas")
                .specialtyIds(List.of(1))
                .build();
        when(specialtyRepository.findAllById(List.of(1))).thenReturn(List.of(radiology));
        Vet saved = Vet.builder()
                .id(3).firstName("Linda").lastName("Douglas")
                .specialties(new HashSet<>(Set.of(radiology)))
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .build();
        when(vetRepository.save(any(Vet.class))).thenReturn(saved);

        VetResponseDto result = vetService.addVet(dto);

        assertThat(result.getId()).isEqualTo(3);
        assertThat(result.getSpecialties()).hasSize(1);
    }

    @Test
    void addVet_withoutSpecialties() {
        VetRequestDto dto = VetRequestDto.builder()
                .firstName("Linda").lastName("Douglas")
                .specialtyIds(null)
                .build();
        Vet saved = Vet.builder()
                .id(3).firstName("Linda").lastName("Douglas")
                .specialties(new HashSet<>())
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .build();
        when(vetRepository.save(any(Vet.class))).thenReturn(saved);

        VetResponseDto result = vetService.addVet(dto);

        assertThat(result.getId()).isEqualTo(3);
        assertThat(result.getSpecialties()).isEmpty();
    }

    @Test
    void addVet_withEmptySpecialtyIds() {
        VetRequestDto dto = VetRequestDto.builder()
                .firstName("Linda").lastName("Douglas")
                .specialtyIds(List.of())
                .build();
        Vet saved = Vet.builder()
                .id(3).firstName("Linda").lastName("Douglas")
                .specialties(new HashSet<>())
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .build();
        when(vetRepository.save(any(Vet.class))).thenReturn(saved);

        VetResponseDto result = vetService.addVet(dto);

        assertThat(result.getSpecialties()).isEmpty();
    }

    @Test
    void addVet_specialtyNotFound() {
        VetRequestDto dto = VetRequestDto.builder()
                .firstName("Linda").lastName("Douglas")
                .specialtyIds(List.of(1, 99))
                .build();
        when(specialtyRepository.findAllById(List.of(1, 99))).thenReturn(List.of(radiology));

        assertThatThrownBy(() -> vetService.addVet(dto))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("One or more specialties not found");
    }

    @Test
    void updateVet_success() {
        when(vetRepository.findById(1)).thenReturn(Optional.of(james));
        when(specialtyRepository.findAllById(List.of(1))).thenReturn(List.of(radiology));
        when(vetRepository.save(any(Vet.class))).thenAnswer(inv -> inv.getArgument(0));

        VetRequestDto dto = VetRequestDto.builder()
                .firstName("UpdatedJames").lastName("UpdatedCarter")
                .specialtyIds(List.of(1))
                .build();
        VetResponseDto result = vetService.updateVet(1, dto);

        assertThat(result.getFirstName()).isEqualTo("UpdatedJames");
        assertThat(result.getLastName()).isEqualTo("UpdatedCarter");
    }

    @Test
    void updateVet_notFound() {
        when(vetRepository.findById(99)).thenReturn(Optional.empty());

        VetRequestDto dto = VetRequestDto.builder()
                .firstName("X").lastName("Y").specialtyIds(List.of()).build();

        assertThatThrownBy(() -> vetService.updateVet(99, dto))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteVet_success() {
        when(vetRepository.findById(1)).thenReturn(Optional.of(james));

        VetResponseDto result = vetService.deleteVet(1);

        assertThat(result.getId()).isEqualTo(1);
        verify(vetRepository).delete(james);
    }

    @Test
    void deleteVet_notFound() {
        when(vetRepository.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> vetService.deleteVet(99))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void searchByName_returnsMatching() {
        when(vetRepository.searchByName("Carter")).thenReturn(List.of(james));

        List<VetResponseDto> result = vetService.searchByName("Carter");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getLastName()).isEqualTo("Carter");
    }

    @Test
    void filterBySpecialty_returnsMatching() {
        when(vetRepository.findBySpecialtyId(1)).thenReturn(List.of(james));

        List<VetResponseDto> result = vetService.filterBySpecialty(1);

        assertThat(result).hasSize(1);
    }
}
