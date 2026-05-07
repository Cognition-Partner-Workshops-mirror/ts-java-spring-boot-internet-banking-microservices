package com.petclinic.vet.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petclinic.vet.dto.SpecialtyRequestDto;
import com.petclinic.vet.dto.SpecialtyResponseDto;
import com.petclinic.vet.exception.GlobalExceptionHandler;
import com.petclinic.vet.exception.ResourceNotFoundException;
import com.petclinic.vet.service.SpecialtyService;
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

@WebMvcTest(SpecialtyController.class)
@Import(GlobalExceptionHandler.class)
class SpecialtyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SpecialtyService specialtyService;

    private SpecialtyResponseDto sampleSpecialty() {
        return SpecialtyResponseDto.builder().id(1).name("radiology").build();
    }

    @Test
    void listSpecialties_returnsAll() throws Exception {
        when(specialtyService.listSpecialties()).thenReturn(List.of(sampleSpecialty()));

        mockMvc.perform(get("/specialties"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name", is("radiology")));
    }

    @Test
    void listSpecialties_searchByName() throws Exception {
        when(specialtyService.searchByName("rad")).thenReturn(List.of(sampleSpecialty()));

        mockMvc.perform(get("/specialties").param("name", "rad"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void getSpecialty_found() throws Exception {
        when(specialtyService.getSpecialty(1)).thenReturn(sampleSpecialty());

        mockMvc.perform(get("/specialties/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.name", is("radiology")));
    }

    @Test
    void getSpecialty_notFound() throws Exception {
        when(specialtyService.getSpecialty(99))
                .thenThrow(new ResourceNotFoundException("Specialty not found with id: 99"));

        mockMvc.perform(get("/specialties/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title", is("Not Found")));
    }

    @Test
    void addSpecialty_success() throws Exception {
        SpecialtyRequestDto request = new SpecialtyRequestDto("dentistry");
        SpecialtyResponseDto response = SpecialtyResponseDto.builder().id(2).name("dentistry").build();
        when(specialtyService.addSpecialty(any(SpecialtyRequestDto.class))).thenReturn(response);

        mockMvc.perform(post("/specialties")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(2)))
                .andExpect(jsonPath("$.name", is("dentistry")));
    }

    @Test
    void addSpecialty_validationError_blankName() throws Exception {
        SpecialtyRequestDto request = new SpecialtyRequestDto("");

        mockMvc.perform(post("/specialties")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title", is("Bad Request")));
    }

    @Test
    void addSpecialty_validationError_nullName() throws Exception {
        SpecialtyRequestDto request = new SpecialtyRequestDto(null);

        mockMvc.perform(post("/specialties")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateSpecialty_success() throws Exception {
        SpecialtyRequestDto request = new SpecialtyRequestDto("updated-radiology");
        SpecialtyResponseDto response = SpecialtyResponseDto.builder().id(1).name("updated-radiology").build();
        when(specialtyService.updateSpecialty(eq(1), any(SpecialtyRequestDto.class))).thenReturn(response);

        mockMvc.perform(put("/specialties/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("updated-radiology")));
    }

    @Test
    void updateSpecialty_notFound() throws Exception {
        SpecialtyRequestDto request = new SpecialtyRequestDto("x");
        when(specialtyService.updateSpecialty(eq(99), any(SpecialtyRequestDto.class)))
                .thenThrow(new ResourceNotFoundException("Specialty not found with id: 99"));

        mockMvc.perform(put("/specialties/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteSpecialty_success() throws Exception {
        when(specialtyService.deleteSpecialty(1)).thenReturn(sampleSpecialty());

        mockMvc.perform(delete("/specialties/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)));
    }

    @Test
    void deleteSpecialty_notFound() throws Exception {
        when(specialtyService.deleteSpecialty(99))
                .thenThrow(new ResourceNotFoundException("Specialty not found with id: 99"));

        mockMvc.perform(delete("/specialties/99"))
                .andExpect(status().isNotFound());
    }
}
