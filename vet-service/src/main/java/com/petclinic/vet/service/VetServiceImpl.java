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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class VetServiceImpl implements VetService {

    private final VetRepository vetRepository;
    private final SpecialtyRepository specialtyRepository;
    private final VetMapper vetMapper;

    @Override
    @Transactional(readOnly = true)
    public List<VetResponseDto> getAllVets() {
        List<Vet> vets = vetRepository.findAll();
        return vetMapper.toResponseDtoList(vets);
    }

    @Override
    @Transactional(readOnly = true)
    public VetResponseDto getVetById(Integer id) {
        Vet vet = vetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vet not found with id: " + id));
        return vetMapper.toResponseDto(vet);
    }

    @Override
    public VetResponseDto createVet(VetRequestDto requestDto) {
        Vet vet = vetMapper.toEntity(requestDto);
        Set<Specialty> specialties = resolveSpecialties(requestDto.getSpecialties());
        vet.setSpecialties(specialties);
        Vet saved = vetRepository.save(vet);
        return vetMapper.toResponseDto(saved);
    }

    @Override
    public VetResponseDto updateVet(Integer id, VetRequestDto requestDto) {
        Vet vet = vetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vet not found with id: " + id));
        vetMapper.updateEntity(vet, requestDto);
        Set<Specialty> specialties = resolveSpecialties(requestDto.getSpecialties());
        vet.setSpecialties(specialties);
        Vet updated = vetRepository.save(vet);
        return vetMapper.toResponseDto(updated);
    }

    @Override
    public VetResponseDto deleteVet(Integer id) {
        Vet vet = vetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vet not found with id: " + id));
        VetResponseDto responseDto = vetMapper.toResponseDto(vet);
        vetRepository.delete(vet);
        return responseDto;
    }

    @Override
    @Transactional(readOnly = true)
    public List<VetResponseDto> findByLastName(String lastName) {
        List<Vet> vets = vetRepository.findByLastNameContainingIgnoreCase(lastName);
        return vetMapper.toResponseDtoList(vets);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VetResponseDto> findBySpecialtyName(String specialtyName) {
        List<Vet> vets = vetRepository.findBySpecialtyName(specialtyName);
        return vetMapper.toResponseDtoList(vets);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VetResponseDto> searchByName(String name) {
        List<Vet> vets = vetRepository.findByNameContaining(name);
        return vetMapper.toResponseDtoList(vets);
    }

    private Set<Specialty> resolveSpecialties(List<SpecialtyResponseDto> specialtyDtos) {
        Set<Specialty> specialties = new HashSet<>();
        if (specialtyDtos == null || specialtyDtos.isEmpty()) {
            return specialties;
        }
        for (SpecialtyResponseDto dto : specialtyDtos) {
            if (dto.getId() != null) {
                Specialty specialty = specialtyRepository.findById(dto.getId())
                        .orElseThrow(() -> new ResourceNotFoundException("Specialty not found with id: " + dto.getId()));
                specialties.add(specialty);
            }
        }
        return specialties;
    }
}
