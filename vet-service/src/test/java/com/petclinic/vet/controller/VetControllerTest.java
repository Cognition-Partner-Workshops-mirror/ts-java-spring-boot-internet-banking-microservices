package com.petclinic.vet.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petclinic.vet.dto.SpecialtyResponseDto;
import com.petclinic.vet.dto.VetRequestDto;
import com.petclinic.vet.dto.VetResponseDto;
import com.petclinic.vet.exception.GlobalExceptionHandler;
import com.petclinic.vet.exception.ResourceNotFoundException;
import com.petclinic.vet.service.VetService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(VetController.class)
@Import(GlobalExceptionHandler.class)
class VetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private VetService vetService;

    private VetResponseDto sampleVetResponse() {
        return VetResponseDto.builder()
                .id(1)
                .firstName("James")
                .lastName("Carter")
                .specialties(List.of(SpecialtyResponseDto.builder().id(1).name("radiology").build()))
                .build();
    }

    @Test
    void listVets_returnsAll() throws Exception {
        when(vetService.listVets()).thenReturn(List.of(sampleVetResponse()));

        mockMvc.perform(get("/api/vets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].firstName").value("James"));
    }

    @Test
    void listVets_filterBySpecialty() throws Exception {
        when(vetService.findBySpecialty(1)).thenReturn(List.of(sampleVetResponse()));

        mockMvc.perform(get("/api/vets").param("specialtyId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].firstName").value("James"));
    }

    @Test
    void listVets_searchByName() throws Exception {
        when(vetService.searchByName("James")).thenReturn(List.of(sampleVetResponse()));

        mockMvc.perform(get("/api/vets").param("name", "James"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].firstName").value("James"));
    }

    @Test
    void getVet_found() throws Exception {
        when(vetService.getVet(1)).thenReturn(sampleVetResponse());

        mockMvc.perform(get("/api/vets/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastName").value("Carter"));
    }

    @Test
    void getVet_notFound() throws Exception {
        when(vetService.getVet(99)).thenThrow(new ResourceNotFoundException("Vet", 99));

        mockMvc.perform(get("/api/vets/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Not Found"));
    }

    @Test
    void addVet_valid() throws Exception {
        VetRequestDto request = VetRequestDto.builder()
                .firstName("James")
                .lastName("Carter")
                .specialtyIds(List.of(1))
                .build();
        when(vetService.createVet(any())).thenReturn(sampleVetResponse());

        mockMvc.perform(post("/api/vets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void addVet_invalidBlankFirstName() throws Exception {
        VetRequestDto request = VetRequestDto.builder()
                .firstName("")
                .lastName("Carter")
                .specialtyIds(List.of())
                .build();

        mockMvc.perform(post("/api/vets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateVet_valid() throws Exception {
        VetRequestDto request = VetRequestDto.builder()
                .firstName("Helen")
                .lastName("Leary")
                .specialtyIds(List.of(1))
                .build();
        VetResponseDto response = VetResponseDto.builder()
                .id(1)
                .firstName("Helen")
                .lastName("Leary")
                .specialties(List.of(SpecialtyResponseDto.builder().id(1).name("radiology").build()))
                .build();
        when(vetService.updateVet(eq(1), any())).thenReturn(response);

        mockMvc.perform(put("/api/vets/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Helen"));
    }

    @Test
    void deleteVet_found() throws Exception {
        when(vetService.deleteVet(1)).thenReturn(sampleVetResponse());

        mockMvc.perform(delete("/api/vets/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }
}
