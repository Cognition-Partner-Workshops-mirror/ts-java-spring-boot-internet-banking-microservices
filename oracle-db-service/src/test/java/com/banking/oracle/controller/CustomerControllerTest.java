package com.banking.oracle.controller;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.banking.oracle.model.CustomerStatus;
import com.banking.oracle.model.dto.CustomerResponse;
import com.banking.oracle.service.CustomerService;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unit tests for CustomerController REST endpoints.
 * Uses MockMvc to test HTTP layer without starting the full application.
 * CustomerService is mocked to isolate controller logic.
 */
@WebMvcTest(CustomerController.class)
@ActiveProfiles("test")
class CustomerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CustomerService customerService;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Tests GET /api/customers - should return all customers with 200 OK status.
     */
    @Test
    void shouldGetAllCustomers() throws Exception {
        /* Arrange - create mock customer responses */
        CustomerResponse customer1 = CustomerResponse.builder()
                .id(1L)
                .firstName("John")
                .lastName("Doe")
                .email("john.doe@example.com")
                .status(CustomerStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        CustomerResponse customer2 = CustomerResponse.builder()
                .id(2L)
                .firstName("Jane")
                .lastName("Smith")
                .email("jane.smith@example.com")
                .status(CustomerStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        List<CustomerResponse> customers = Arrays.asList(customer1, customer2);
        Mockito.when(customerService.getAllCustomers()).thenReturn(customers);

        /* Act & Assert - verify HTTP response */
        mockMvc.perform(get("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].firstName").value("John"))
                .andExpect(jsonPath("$[1].firstName").value("Jane"));
    }

    /**
     * Tests POST /api/customers with invalid request body (missing required fields).
     * Should return 400 Bad Request with validation error details.
     */
    @Test
    void shouldReturnBadRequestForInvalidCustomer() throws Exception {
        /* Arrange - create an invalid request with empty required fields */
        String invalidRequest = "{\"firstName\":\"\", \"lastName\":\"\", \"email\":\"invalid\"}";

        /* Act & Assert - verify validation errors are returned */
        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequest))
                .andExpect(status().isBadRequest());
    }
}
