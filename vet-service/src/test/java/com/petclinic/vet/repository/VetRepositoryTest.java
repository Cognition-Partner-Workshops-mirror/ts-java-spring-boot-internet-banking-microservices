package com.petclinic.vet.repository;

import com.petclinic.vet.entity.Specialty;
import com.petclinic.vet.entity.Vet;
import com.petclinic.vet.config.JpaAuditingConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
class VetRepositoryTest {

    @Autowired
    private VetRepository vetRepository;

    @Autowired
    private SpecialtyRepository specialtyRepository;

    @Test
    void findAll_returnsSeededVets() {
        List<Vet> vets = vetRepository.findAll();
        assertThat(vets).hasSizeGreaterThanOrEqualTo(3);
    }

    @Test
    void findBySpecialtyId_found() {
        List<Vet> result = vetRepository.findBySpecialtyId(1);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getLastName()).isEqualTo("Leary");
    }

    @Test
    void findBySpecialtyId_notFound() {
        List<Vet> result = vetRepository.findBySpecialtyId(999);
        assertThat(result).isEmpty();
    }

    @Test
    void findByNameContaining_firstName() {
        List<Vet> result = vetRepository.findByNameContaining("James");
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFirstName()).isEqualTo("James");
    }

    @Test
    void findByNameContaining_lastName() {
        List<Vet> result = vetRepository.findByNameContaining("Carter");
        assertThat(result).hasSize(1);
    }

    @Test
    void findByNameContaining_caseInsensitive() {
        List<Vet> result = vetRepository.findByNameContaining("james");
        assertThat(result).hasSize(1);
    }

    @Test
    void findByNameContaining_notFound() {
        List<Vet> result = vetRepository.findByNameContaining("xyz");
        assertThat(result).isEmpty();
    }

    @Test
    void save_createsNewVet() {
        Specialty specialty = specialtyRepository.findById(1).orElseThrow();
        Vet newVet = Vet.builder()
                .firstName("Anna")
                .lastName("Smith")
                .specialties(Set.of(specialty))
                .build();
        Vet saved = vetRepository.save(newVet);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getFirstName()).isEqualTo("Anna");
    }

    @Test
    void findById_found() {
        assertThat(vetRepository.findById(1)).isPresent();
    }

    @Test
    void delete_removesVet() {
        Vet vet = vetRepository.findById(1).orElseThrow();
        vetRepository.delete(vet);
        assertThat(vetRepository.findById(1)).isEmpty();
    }
}
