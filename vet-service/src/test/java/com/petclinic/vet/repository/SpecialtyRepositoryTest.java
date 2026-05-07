package com.petclinic.vet.repository;

import com.petclinic.vet.entity.Specialty;
import com.petclinic.vet.config.JpaAuditingConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
class SpecialtyRepositoryTest {

    @Autowired
    private SpecialtyRepository specialtyRepository;

    @Test
    void findAll_returnsSeededData() {
        List<Specialty> specialties = specialtyRepository.findAll();
        assertThat(specialties).hasSizeGreaterThanOrEqualTo(3);
    }

    @Test
    void findByNameContainingIgnoreCase_found() {
        List<Specialty> result = specialtyRepository.findByNameContainingIgnoreCase("radio");
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("radiology");
    }

    @Test
    void findByNameContainingIgnoreCase_notFound() {
        List<Specialty> result = specialtyRepository.findByNameContainingIgnoreCase("xyz");
        assertThat(result).isEmpty();
    }

    @Test
    void findById_found() {
        assertThat(specialtyRepository.findById(1)).isPresent();
    }

    @Test
    void findById_notFound() {
        assertThat(specialtyRepository.findById(999)).isEmpty();
    }

    @Test
    void save_createsNewSpecialty() {
        Specialty newSpec = Specialty.builder().name("oncology").build();
        Specialty saved = specialtyRepository.save(newSpec);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getName()).isEqualTo("oncology");
    }

    @Test
    void delete_removesSpecialty() {
        Specialty spec = specialtyRepository.findById(1).orElseThrow();
        specialtyRepository.delete(spec);
        assertThat(specialtyRepository.findById(1)).isEmpty();
    }
}
