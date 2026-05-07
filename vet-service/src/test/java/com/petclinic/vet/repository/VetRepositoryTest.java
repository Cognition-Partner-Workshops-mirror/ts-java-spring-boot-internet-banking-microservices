package com.petclinic.vet.repository;

import com.petclinic.vet.entity.Specialty;
import com.petclinic.vet.entity.Vet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class VetRepositoryTest {

    @Autowired
    private VetRepository vetRepository;

    @Autowired
    private SpecialtyRepository specialtyRepository;

    private Specialty radiology;
    private Specialty surgery;

    @BeforeEach
    void setUp() {
        vetRepository.deleteAll();
        specialtyRepository.deleteAll();

        radiology = specialtyRepository.save(Specialty.builder().name("radiology").build());
        surgery = specialtyRepository.save(Specialty.builder().name("surgery").build());

        vetRepository.save(Vet.builder()
                .firstName("James").lastName("Carter")
                .specialties(new HashSet<>(Set.of(radiology)))
                .build());
        vetRepository.save(Vet.builder()
                .firstName("Helen").lastName("Leary")
                .specialties(new HashSet<>(Set.of(radiology, surgery)))
                .build());
        vetRepository.save(Vet.builder()
                .firstName("Linda").lastName("Douglas")
                .specialties(new HashSet<>())
                .build());
    }

    @Test
    void findAll_returnsAllVets() {
        List<Vet> result = vetRepository.findAll();
        assertThat(result).hasSize(3);
    }

    @Test
    void findByLastNameContainingIgnoreCase_matchesPartial() {
        List<Vet> result = vetRepository.findByLastNameContainingIgnoreCase("cart");
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getLastName()).isEqualTo("Carter");
    }

    @Test
    void findByLastNameContainingIgnoreCase_noMatch() {
        List<Vet> result = vetRepository.findByLastNameContainingIgnoreCase("xyz");
        assertThat(result).isEmpty();
    }

    @Test
    void findBySpecialtyId_returnsVetsWithSpecialty() {
        List<Vet> result = vetRepository.findBySpecialtyId(radiology.getId());
        assertThat(result).hasSize(2);
    }

    @Test
    void findBySpecialtyId_noMatch() {
        Specialty dentistry = specialtyRepository.save(Specialty.builder().name("dentistry").build());
        List<Vet> result = vetRepository.findBySpecialtyId(dentistry.getId());
        assertThat(result).isEmpty();
    }

    @Test
    void findBySpecialtyName_returnsVetsWithSpecialty() {
        List<Vet> result = vetRepository.findBySpecialtyName("surgery");
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFirstName()).isEqualTo("Helen");
    }

    @Test
    void findBySpecialtyName_caseInsensitive() {
        List<Vet> result = vetRepository.findBySpecialtyName("RADIOLOGY");
        assertThat(result).hasSize(2);
    }

    @Test
    void searchByName_matchesFirstName() {
        List<Vet> result = vetRepository.searchByName("James");
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFirstName()).isEqualTo("James");
    }

    @Test
    void searchByName_matchesLastName() {
        List<Vet> result = vetRepository.searchByName("Leary");
        assertThat(result).hasSize(1);
    }

    @Test
    void searchByName_partialMatch() {
        List<Vet> result = vetRepository.searchByName("el");
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFirstName()).isEqualTo("Helen");
    }

    @Test
    void searchByName_noMatch() {
        List<Vet> result = vetRepository.searchByName("xyz");
        assertThat(result).isEmpty();
    }

    @Test
    void save_persistsVet() {
        Vet vet = vetRepository.save(Vet.builder()
                .firstName("New").lastName("Vet")
                .specialties(new HashSet<>())
                .build());
        assertThat(vet.getId()).isNotNull();
        assertThat(vet.getCreatedAt()).isNotNull();
        assertThat(vet.getUpdatedAt()).isNotNull();
    }

    @Test
    void delete_removesVet() {
        Vet vet = vetRepository.save(Vet.builder()
                .firstName("Temp").lastName("Vet")
                .specialties(new HashSet<>())
                .build());
        Integer id = vet.getId();
        vetRepository.delete(vet);
        assertThat(vetRepository.findById(id)).isEmpty();
    }
}
