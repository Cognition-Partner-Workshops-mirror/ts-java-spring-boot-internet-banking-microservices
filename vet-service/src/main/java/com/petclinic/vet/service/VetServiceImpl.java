package com.petclinic.vet.service;

import com.petclinic.vet.dto.VetRequestDto;
import com.petclinic.vet.dto.VetResponseDto;
import com.petclinic.vet.entity.Specialty;
import com.petclinic.vet.entity.Vet;
import com.petclinic.vet.exception.ResourceNotFoundException;
import com.petclinic.vet.mapper.VetMapper;
import com.petclinic.vet.repository.SpecialtyRepository;
import com.petclinic.vet.repository.VetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@Transactional
public class VetServiceImpl implements VetService {

    private final VetRepository vetRepository;
    private final SpecialtyRepository specialtyRepository;

    public VetServiceImpl(VetRepository vetRepository,
                          SpecialtyRepository specialtyRepository) {
        this.vetRepository = vetRepository;
        this.specialtyRepository = specialtyRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<VetResponseDto> listVets() {
        return VetMapper.toResponseDtoList(vetRepository.findAll());
    }

    @Override
    @Transactional(readOnly = true)
    public VetResponseDto getVet(Integer id) {
        Vet vet = vetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vet not found with id: " + id));
        return VetMapper.toResponseDto(vet);
    }

    @Override
    public VetResponseDto addVet(VetRequestDto dto) {
        Set<Specialty> specialties = resolveSpecialties(dto.getSpecialtyIds());
        Vet vet = Vet.builder()
                .firstName(dto.getFirstName())
                .lastName(dto.getLastName())
                .specialties(specialties)
                .build();
        Vet saved = vetRepository.save(vet);
        return VetMapper.toResponseDto(saved);
    }

    @Override
    public VetResponseDto updateVet(Integer id, VetRequestDto dto) {
        Vet vet = vetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vet not found with id: " + id));
        vet.setFirstName(dto.getFirstName());
        vet.setLastName(dto.getLastName());
        vet.setSpecialties(resolveSpecialties(dto.getSpecialtyIds()));
        Vet updated = vetRepository.save(vet);
        return VetMapper.toResponseDto(updated);
    }

    @Override
    public VetResponseDto deleteVet(Integer id) {
        Vet vet = vetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vet not found with id: " + id));
        vetRepository.delete(vet);
        return VetMapper.toResponseDto(vet);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VetResponseDto> searchByName(String name) {
        return VetMapper.toResponseDtoList(vetRepository.searchByName(name));
    }

    @Override
    @Transactional(readOnly = true)
    public List<VetResponseDto> filterBySpecialty(Integer specialtyId) {
        return VetMapper.toResponseDtoList(vetRepository.findBySpecialtyId(specialtyId));
    }

    private Set<Specialty> resolveSpecialties(List<Integer> specialtyIds) {
        if (specialtyIds == null || specialtyIds.isEmpty()) {
            return new HashSet<>();
        }
        List<Specialty> found = specialtyRepository.findAllById(specialtyIds);
        if (found.size() != specialtyIds.size()) {
            throw new ResourceNotFoundException("One or more specialties not found");
        }
        return new HashSet<>(found);
    }
}
