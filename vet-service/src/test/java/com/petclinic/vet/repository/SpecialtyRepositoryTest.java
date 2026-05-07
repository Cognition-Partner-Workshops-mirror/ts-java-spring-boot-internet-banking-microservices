package com.petclinic.vet.repository;

import com.petclinic.vet.entity.Specialty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class SpecialtyRepositoryTest {

    @Autowired
    private SpecialtyRepository specialtyRepository;

    @BeforeEach
    void setUp() {
        specialtyRepository.deleteAll();
        specialtyRepository.save(Specialty.builder().name("radiology").build());
        specialtyRepository.save(Specialty.builder().name("surgery").build());
        specialtyRepository.save(Specialty.builder().name("dentistry").build());
    }

    @Test
    void findAll_returnsAllSpecialties() {
        List<Specialty> result = specialtyRepository.findAll();
        assertThat(result).hasSize(3);
    }

    @Test
    void findByNameContainingIgnoreCase_matchesPartial() {
        List<Specialty> result = specialtyRepository.findByNameContainingIgnoreCase("rad");
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("radiology");
    }

    @Test
    void findByNameContainingIgnoreCase_caseInsensitive() {
        List<Specialty> result = specialtyRepository.findByNameContainingIgnoreCase("SURGERY");
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("surgery");
    }

    @Test
    void findByNameContainingIgnoreCase_noMatch() {
        List<Specialty> result = specialtyRepository.findByNameContainingIgnoreCase("xyz");
        assertThat(result).isEmpty();
    }

    @Test
    void save_persistsSpecialty() {
        Specialty neurology = specialtyRepository.save(Specialty.builder().name("neurology").build());
        assertThat(neurology.getId()).isNotNull();
        assertThat(neurology.getCreatedAt()).isNotNull();
        assertThat(neurology.getUpdatedAt()).isNotNull();
    }

    @Test
    void findById_returnsSpecialty() {
        Specialty saved = specialtyRepository.save(Specialty.builder().name("cardiology").build());
        assertThat(specialtyRepository.findById(saved.getId())).isPresent();
    }

    @Test
    void delete_removesSpecialty() {
        Specialty saved = specialtyRepository.save(Specialty.builder().name("temporary").build());
        Integer id = saved.getId();
        specialtyRepository.delete(saved);
        assertThat(specialtyRepository.findById(id)).isEmpty();
    }
}
