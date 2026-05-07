package com.petclinic.vet.repository;

import com.petclinic.vet.entity.Specialty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class SpecialtyRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private SpecialtyRepository specialtyRepository;

    private Specialty radiology;
    private Specialty surgery;

    @BeforeEach
    void setUp() {
        radiology = Specialty.builder().name("radiology").build();
        surgery = Specialty.builder().name("surgery").build();
        entityManager.persist(radiology);
        entityManager.persist(surgery);
        entityManager.flush();
    }

    @Test
    void findByNameIgnoreCase_shouldFindExactMatch() {
        Optional<Specialty> result = specialtyRepository.findByNameIgnoreCase("radiology");
        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("radiology");
    }

    @Test
    void findByNameIgnoreCase_shouldBeCaseInsensitive() {
        Optional<Specialty> result = specialtyRepository.findByNameIgnoreCase("RADIOLOGY");
        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("radiology");
    }

    @Test
    void findByNameIgnoreCase_shouldReturnEmptyForNoMatch() {
        Optional<Specialty> result = specialtyRepository.findByNameIgnoreCase("dentistry");
        assertThat(result).isEmpty();
    }

    @Test
    void findByNameContainingIgnoreCase_shouldFindPartialMatch() {
        List<Specialty> result = specialtyRepository.findByNameContainingIgnoreCase("radio");
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("radiology");
    }

    @Test
    void findByNameContainingIgnoreCase_shouldBeCaseInsensitive() {
        List<Specialty> result = specialtyRepository.findByNameContainingIgnoreCase("SURG");
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("surgery");
    }

    @Test
    void findByNameContainingIgnoreCase_shouldReturnEmptyForNoMatch() {
        List<Specialty> result = specialtyRepository.findByNameContainingIgnoreCase("xyz");
        assertThat(result).isEmpty();
    }

    @Test
    void findAll_shouldReturnAllSpecialties() {
        List<Specialty> result = specialtyRepository.findAll();
        assertThat(result).hasSize(2);
    }

    @Test
    void save_shouldPersistNewSpecialty() {
        Specialty dentistry = Specialty.builder().name("dentistry").build();
        Specialty saved = specialtyRepository.save(dentistry);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getName()).isEqualTo("dentistry");
    }

    @Test
    void delete_shouldRemoveSpecialty() {
        specialtyRepository.delete(radiology);
        entityManager.flush();
        Optional<Specialty> result = specialtyRepository.findById(radiology.getId());
        assertThat(result).isEmpty();
    }
}
