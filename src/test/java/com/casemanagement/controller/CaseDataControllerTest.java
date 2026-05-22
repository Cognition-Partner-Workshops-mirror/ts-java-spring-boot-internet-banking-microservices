package com.casemanagement.controller;

import com.casemanagement.dto.CaseDataRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for CaseDataController.
 * Tests both Set 1 and Set 2 endpoints with the H2 in-memory database.
 * Verifies that SM_USER header is required and data is correctly persisted.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CaseDataControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Test: Submit case data to Set 1 - should return 201 Created.
     */
    @Test
    void testCreateCaseDataSet1_Success() throws Exception {
        CaseDataRequest request = new CaseDataRequest(
                "CASE-001", "1.0", "Agent Smith", "PARTY-12345");

        mockMvc.perform(post("/api/v1/case-data/set1")
                        .header("SM_USER", "testuser")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.caseNumber").value("CASE-001"))
                .andExpect(jsonPath("$.versionNumber").value("1.0"))
                .andExpect(jsonPath("$.agentName").value("Agent Smith"))
                .andExpect(jsonPath("$.partyIdentifier").value("PARTY-12345"))
                .andExpect(jsonPath("$.smUser").value("testuser"))
                .andExpect(jsonPath("$.dataSet").value("SET1"))
                .andExpect(jsonPath("$.message").value("Case data successfully stored in Set 1"));
    }

    /**
     * Test: Submit case data to Set 2 - should return 201 Created.
     */
    @Test
    void testCreateCaseDataSet2_Success() throws Exception {
        CaseDataRequest request = new CaseDataRequest(
                "CASE-002", "2.0", "Agent Jones", "PARTY-67890");

        mockMvc.perform(post("/api/v1/case-data/set2")
                        .header("SM_USER", "testuser2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.caseNumber").value("CASE-002"))
                .andExpect(jsonPath("$.dataSet").value("SET2"))
                .andExpect(jsonPath("$.smUser").value("testuser2"));
    }

    /**
     * Test: Missing SM_USER header should return 400 Bad Request.
     */
    @Test
    void testCreateCaseData_MissingSmUserHeader() throws Exception {
        CaseDataRequest request = new CaseDataRequest(
                "CASE-003", "1.0", "Agent X", "PARTY-99999");

        mockMvc.perform(post("/api/v1/case-data/set1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    /**
     * Test: Invalid request body (missing required fields) should return 400.
     */
    @Test
    void testCreateCaseData_ValidationError() throws Exception {
        // Empty case number should fail validation
        CaseDataRequest request = new CaseDataRequest(
                "", "1.0", "Agent X", "PARTY-99999");

        mockMvc.perform(post("/api/v1/case-data/set1")
                        .header("SM_USER", "testuser")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    /**
     * Test: GET Set 1 data should return 200 OK with list.
     */
    @Test
    void testGetAllCaseDataSet1() throws Exception {
        mockMvc.perform(get("/api/v1/case-data/set1")
                        .header("SM_USER", "testuser"))
                .andExpect(status().isOk());
    }

    /**
     * Test: GET Set 2 data should return 200 OK with list.
     */
    @Test
    void testGetAllCaseDataSet2() throws Exception {
        mockMvc.perform(get("/api/v1/case-data/set2")
                        .header("SM_USER", "testuser"))
                .andExpect(status().isOk());
    }

    /**
     * Test: Search Set 1 by case number should return 200 OK.
     */
    @Test
    void testSearchCaseDataSet1() throws Exception {
        mockMvc.perform(get("/api/v1/case-data/set1/search")
                        .header("SM_USER", "testuser")
                        .param("caseNumber", "CASE-001"))
                .andExpect(status().isOk());
    }
}
