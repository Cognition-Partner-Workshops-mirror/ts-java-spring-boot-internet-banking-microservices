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

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
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

    private VetResponseDto sampleVet() {
        return VetResponseDto.builder()
                .id(1).firstName("James").lastName("Carter")
                .specialties(List.of(
                        SpecialtyResponseDto.builder().id(1).name("radiology").build()
                ))
                .build();
    }

    @Test
    void listVets_returnsAll() throws Exception {
        when(vetService.listVets()).thenReturn(List.of(sampleVet()));

        mockMvc.perform(get("/vets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].firstName", is("James")));
    }

    @Test
    void listVets_searchByName() throws Exception {
        when(vetService.searchByName("Carter")).thenReturn(List.of(sampleVet()));

        mockMvc.perform(get("/vets").param("name", "Carter"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void listVets_filterBySpecialty() throws Exception {
        when(vetService.filterBySpecialty(1)).thenReturn(List.of(sampleVet()));

        mockMvc.perform(get("/vets").param("specialtyId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void getVet_found() throws Exception {
        when(vetService.getVet(1)).thenReturn(sampleVet());

        mockMvc.perform(get("/vets/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.firstName", is("James")))
                .andExpect(jsonPath("$.specialties", hasSize(1)));
    }

    @Test
    void getVet_notFound() throws Exception {
        when(vetService.getVet(99)).thenThrow(new ResourceNotFoundException("Vet not found with id: 99"));

        mockMvc.perform(get("/vets/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title", is("Not Found")))
                .andExpect(jsonPath("$.detail", is("Vet not found with id: 99")));
    }

    @Test
    void addVet_success() throws Exception {
        VetRequestDto request = VetRequestDto.builder()
                .firstName("James").lastName("Carter")
                .specialtyIds(List.of(1))
                .build();
        when(vetService.addVet(any(VetRequestDto.class))).thenReturn(sampleVet());

        mockMvc.perform(post("/vets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.firstName", is("James")));
    }

    @Test
    void addVet_validationError_blankFirstName() throws Exception {
        VetRequestDto request = VetRequestDto.builder()
                .firstName("").lastName("Carter")
                .specialtyIds(List.of())
                .build();

        mockMvc.perform(post("/vets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title", is("Bad Request")));
    }

    @Test
    void addVet_validationError_blankLastName() throws Exception {
        VetRequestDto request = VetRequestDto.builder()
                .firstName("James").lastName("")
                .specialtyIds(List.of())
                .build();

        mockMvc.perform(post("/vets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateVet_success() throws Exception {
        VetRequestDto request = VetRequestDto.builder()
                .firstName("Updated").lastName("Carter")
                .specialtyIds(List.of(1))
                .build();
        VetResponseDto updated = VetResponseDto.builder()
                .id(1).firstName("Updated").lastName("Carter")
                .specialties(List.of(
                        SpecialtyResponseDto.builder().id(1).name("radiology").build()
                ))
                .build();
        when(vetService.updateVet(eq(1), any(VetRequestDto.class))).thenReturn(updated);

        mockMvc.perform(put("/vets/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName", is("Updated")));
    }

    @Test
    void updateVet_notFound() throws Exception {
        VetRequestDto request = VetRequestDto.builder()
                .firstName("James").lastName("Carter")
                .specialtyIds(List.of())
                .build();
        when(vetService.updateVet(eq(99), any(VetRequestDto.class)))
                .thenThrow(new ResourceNotFoundException("Vet not found with id: 99"));

        mockMvc.perform(put("/vets/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteVet_success() throws Exception {
        when(vetService.deleteVet(1)).thenReturn(sampleVet());

        mockMvc.perform(delete("/vets/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)));
    }

    @Test
    void deleteVet_notFound() throws Exception {
        when(vetService.deleteVet(99))
                .thenThrow(new ResourceNotFoundException("Vet not found with id: 99"));

        mockMvc.perform(delete("/vets/99"))
                .andExpect(status().isNotFound());
    }
}
