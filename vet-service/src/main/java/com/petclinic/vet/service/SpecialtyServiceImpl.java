package com.petclinic.vet.service;

import com.petclinic.vet.dto.SpecialtyRequestDto;
import com.petclinic.vet.dto.SpecialtyResponseDto;
import com.petclinic.vet.entity.Specialty;
import com.petclinic.vet.exception.ResourceNotFoundException;
import com.petclinic.vet.mapper.SpecialtyMapper;
import com.petclinic.vet.repository.SpecialtyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class SpecialtyServiceImpl implements SpecialtyService {

    private final SpecialtyRepository specialtyRepository;

    public SpecialtyServiceImpl(SpecialtyRepository specialtyRepository) {
        this.specialtyRepository = specialtyRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SpecialtyResponseDto> listSpecialties() {
        return SpecialtyMapper.toResponseDtoList(specialtyRepository.findAll());
    }

    @Override
    @Transactional(readOnly = true)
    public SpecialtyResponseDto getSpecialty(Integer id) {
        Specialty specialty = specialtyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Specialty not found with id: " + id));
        return SpecialtyMapper.toResponseDto(specialty);
    }

    @Override
    public SpecialtyResponseDto addSpecialty(SpecialtyRequestDto dto) {
        Specialty specialty = SpecialtyMapper.toEntity(dto);
        Specialty saved = specialtyRepository.save(specialty);
        return SpecialtyMapper.toResponseDto(saved);
    }

    @Override
    public SpecialtyResponseDto updateSpecialty(Integer id, SpecialtyRequestDto dto) {
        Specialty specialty = specialtyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Specialty not found with id: " + id));
        SpecialtyMapper.updateEntity(specialty, dto);
        Specialty updated = specialtyRepository.save(specialty);
        return SpecialtyMapper.toResponseDto(updated);
    }

    @Override
    public SpecialtyResponseDto deleteSpecialty(Integer id) {
        Specialty specialty = specialtyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Specialty not found with id: " + id));
        specialtyRepository.delete(specialty);
        return SpecialtyMapper.toResponseDto(specialty);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SpecialtyResponseDto> searchByName(String name) {
        return SpecialtyMapper.toResponseDtoList(
                specialtyRepository.findByNameContainingIgnoreCase(name));
    }
}
