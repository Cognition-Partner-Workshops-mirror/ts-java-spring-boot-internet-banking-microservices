package com.petclinic.vet.controller;

import com.petclinic.vet.dto.VetRequestDto;
import com.petclinic.vet.dto.VetResponseDto;
import com.petclinic.vet.service.VetService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/vets")
public class VetController {

    private final VetService vetService;

    public VetController(VetService vetService) {
        this.vetService = vetService;
    }

    @GetMapping
    public ResponseEntity<List<VetResponseDto>> listVets(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Integer specialtyId) {
        if (name != null && !name.isBlank()) {
            return ResponseEntity.ok(vetService.searchByName(name));
        }
        if (specialtyId != null) {
            return ResponseEntity.ok(vetService.filterBySpecialty(specialtyId));
        }
        return ResponseEntity.ok(vetService.listVets());
    }

    @GetMapping("/{vetId}")
    public ResponseEntity<VetResponseDto> getVet(@PathVariable Integer vetId) {
        return ResponseEntity.ok(vetService.getVet(vetId));
    }

    @PostMapping
    public ResponseEntity<VetResponseDto> addVet(@Valid @RequestBody VetRequestDto dto) {
        return ResponseEntity.ok(vetService.addVet(dto));
    }

    @PutMapping("/{vetId}")
    public ResponseEntity<VetResponseDto> updateVet(@PathVariable Integer vetId,
                                                    @Valid @RequestBody VetRequestDto dto) {
        return ResponseEntity.ok(vetService.updateVet(vetId, dto));
    }

    @DeleteMapping("/{vetId}")
    public ResponseEntity<VetResponseDto> deleteVet(@PathVariable Integer vetId) {
        return ResponseEntity.ok(vetService.deleteVet(vetId));
    }
}
