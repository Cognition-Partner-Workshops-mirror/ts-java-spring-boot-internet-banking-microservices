package com.petclinic.vet.repository;

import com.petclinic.vet.entity.Specialty;
import com.petclinic.vet.entity.Vet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class VetRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private VetRepository vetRepository;

    @Autowired
    private SpecialtyRepository specialtyRepository;

    private Specialty radiology;
    private Specialty surgery;
    private Vet vet1;
    private Vet vet2;

    @BeforeEach
    void setUp() {
        radiology = Specialty.builder().name("radiology").build();
        surgery = Specialty.builder().name("surgery").build();
        entityManager.persist(radiology);
        entityManager.persist(surgery);

        vet1 = Vet.builder()
                .firstName("James")
                .lastName("Carter")
                .specialties(Set.of(radiology))
                .build();
        entityManager.persist(vet1);

        vet2 = Vet.builder()
                .firstName("Helen")
                .lastName("Leary")
                .specialties(Set.of(radiology, surgery))
                .build();
        entityManager.persist(vet2);

        entityManager.flush();
    }

    @Test
    void findByLastNameContainingIgnoreCase_shouldFindByPartialLastName() {
        List<Vet> result = vetRepository.findByLastNameContainingIgnoreCase("cart");
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getLastName()).isEqualTo("Carter");
    }

    @Test
    void findByLastNameContainingIgnoreCase_shouldBeCaseInsensitive() {
        List<Vet> result = vetRepository.findByLastNameContainingIgnoreCase("CARTER");
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getLastName()).isEqualTo("Carter");
    }

    @Test
    void findByLastNameContainingIgnoreCase_shouldReturnEmptyForNoMatch() {
        List<Vet> result = vetRepository.findByLastNameContainingIgnoreCase("Smith");
        assertThat(result).isEmpty();
    }

    @Test
    void findBySpecialtyId_shouldReturnVetsWithSpecialty() {
        List<Vet> result = vetRepository.findBySpecialtyId(radiology.getId());
        assertThat(result).hasSize(2);
    }

    @Test
    void findBySpecialtyId_shouldReturnOnlyMatchingVets() {
        List<Vet> result = vetRepository.findBySpecialtyId(surgery.getId());
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFirstName()).isEqualTo("Helen");
    }

    @Test
    void findBySpecialtyName_shouldReturnVetsWithSpecialtyName() {
        List<Vet> result = vetRepository.findBySpecialtyName("radiology");
        assertThat(result).hasSize(2);
    }

    @Test
    void findBySpecialtyName_shouldBeCaseInsensitive() {
        List<Vet> result = vetRepository.findBySpecialtyName("SURGERY");
        assertThat(result).hasSize(1);
    }

    @Test
    void findByNameContaining_shouldFindByFirstName() {
        List<Vet> result = vetRepository.findByNameContaining("James");
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFirstName()).isEqualTo("James");
    }

    @Test
    void findByNameContaining_shouldFindByLastName() {
        List<Vet> result = vetRepository.findByNameContaining("Leary");
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getLastName()).isEqualTo("Leary");
    }

    @Test
    void findByNameContaining_shouldBeCaseInsensitive() {
        List<Vet> result = vetRepository.findByNameContaining("james");
        assertThat(result).hasSize(1);
    }
}
