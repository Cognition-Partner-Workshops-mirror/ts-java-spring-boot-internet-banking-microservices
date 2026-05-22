package com.casemanagement.controller;

import com.casemanagement.dto.CaseDataRequest;
import com.casemanagement.dto.CaseDataResponse;
import com.casemanagement.service.CaseDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST Controller for Case Data endpoints.
 * Provides two sets of endpoints (Set 1 and Set 2) for receiving and storing
 * case data (case number, version number, agent name, party identifier).
 *
 * Every request requires the SM_USER header which identifies the authenticated user.
 * All requests are automatically logged by the ApiRequestLoggingInterceptor.
 *
 * Endpoint mapping:
 *   POST /api/v1/case-data/set1 - Submit case data to Set 1
 *   GET  /api/v1/case-data/set1 - Retrieve all Set 1 data
 *   GET  /api/v1/case-data/set1/search - Search Set 1 by case number
 *   POST /api/v1/case-data/set2 - Submit case data to Set 2
 *   GET  /api/v1/case-data/set2 - Retrieve all Set 2 data
 *   GET  /api/v1/case-data/set2/search - Search Set 2 by case number
 */
@RestController
@RequestMapping("/api/v1/case-data")
@Tag(name = "Case Data", description = "Endpoints for submitting and retrieving case data (Set 1 and Set 2)")
public class CaseDataController {

    private static final Logger logger = LoggerFactory.getLogger(CaseDataController.class);

    private final CaseDataService caseDataService;

    @Autowired
    public CaseDataController(CaseDataService caseDataService) {
        this.caseDataService = caseDataService;
    }

    // ========================================================================
    // SET 1 ENDPOINTS - Case Data Set 1
    // ========================================================================

    /**
     * Submit case data to Set 1.
     * Receives case number, version number, agent name, and party identifier.
     * Data is passed to Oracle via the CASE_DATA_PKG stored procedure.
     *
     * @param smUser  the authenticated user from SM_USER header (required)
     * @param request the case data request body
     * @return the created case data response with HTTP 201 status
     */
    @Operation(summary = "Submit case data to Set 1", description = "Receives case data and stores it in CASE_DATA_SET1 table via Oracle package")
    @PostMapping("/set1")
    public ResponseEntity<CaseDataResponse> createCaseDataSet1(
            @RequestHeader(value = "SM_USER", required = true) String smUser,
            @Valid @RequestBody CaseDataRequest request) {

        logger.info("POST /api/v1/case-data/set1 - SM_USER: {}, Request: {}", smUser, request);

        CaseDataResponse response = caseDataService.processCaseDataSet1(request, smUser);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    /**
     * Retrieve all case data from Set 1.
     *
     * @param smUser the authenticated user from SM_USER header (required)
     * @return list of all Set 1 case data
     */
    @Operation(summary = "Retrieve all Set 1 data", description = "Returns all case data records from Set 1")
    @GetMapping("/set1")
    public ResponseEntity<List<CaseDataResponse>> getAllCaseDataSet1(
            @RequestHeader(value = "SM_USER", required = true) String smUser) {

        logger.info("GET /api/v1/case-data/set1 - SM_USER: {}", smUser);

        List<CaseDataResponse> data = caseDataService.getAllSet1Data();
        return ResponseEntity.ok(data);
    }

    /**
     * Search Set 1 data by case number.
     *
     * @param smUser     the authenticated user from SM_USER header (required)
     * @param caseNumber the case number to search for
     * @return list of matching Set 1 case data
     */
    @Operation(summary = "Search Set 1 by case number", description = "Find Set 1 records matching the given case number")
    @GetMapping("/set1/search")
    public ResponseEntity<List<CaseDataResponse>> searchCaseDataSet1(
            @RequestHeader(value = "SM_USER", required = true) String smUser,
            @RequestParam("caseNumber") String caseNumber) {

        logger.info("GET /api/v1/case-data/set1/search - SM_USER: {}, caseNumber: {}", smUser, caseNumber);

        List<CaseDataResponse> data = caseDataService.getSet1DataByCaseNumber(caseNumber);
        return ResponseEntity.ok(data);
    }

    // ========================================================================
    // SET 2 ENDPOINTS - Case Data Set 2
    // ========================================================================

    /**
     * Submit case data to Set 2.
     * Receives case number, version number, agent name, and party identifier.
     * Data is passed to Oracle via the CASE_DATA_PKG stored procedure.
     *
     * @param smUser  the authenticated user from SM_USER header (required)
     * @param request the case data request body
     * @return the created case data response with HTTP 201 status
     */
    @Operation(summary = "Submit case data to Set 2", description = "Receives case data and stores it in CASE_DATA_SET2 table via Oracle package")
    @PostMapping("/set2")
    public ResponseEntity<CaseDataResponse> createCaseDataSet2(
            @RequestHeader(value = "SM_USER", required = true) String smUser,
            @Valid @RequestBody CaseDataRequest request) {

        logger.info("POST /api/v1/case-data/set2 - SM_USER: {}, Request: {}", smUser, request);

        CaseDataResponse response = caseDataService.processCaseDataSet2(request, smUser);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    /**
     * Retrieve all case data from Set 2.
     *
     * @param smUser the authenticated user from SM_USER header (required)
     * @return list of all Set 2 case data
     */
    @Operation(summary = "Retrieve all Set 2 data", description = "Returns all case data records from Set 2")
    @GetMapping("/set2")
    public ResponseEntity<List<CaseDataResponse>> getAllCaseDataSet2(
            @RequestHeader(value = "SM_USER", required = true) String smUser) {

        logger.info("GET /api/v1/case-data/set2 - SM_USER: {}", smUser);

        List<CaseDataResponse> data = caseDataService.getAllSet2Data();
        return ResponseEntity.ok(data);
    }

    /**
     * Search Set 2 data by case number.
     *
     * @param smUser     the authenticated user from SM_USER header (required)
     * @param caseNumber the case number to search for
     * @return list of matching Set 2 case data
     */
    @Operation(summary = "Search Set 2 by case number", description = "Find Set 2 records matching the given case number")
    @GetMapping("/set2/search")
    public ResponseEntity<List<CaseDataResponse>> searchCaseDataSet2(
            @RequestHeader(value = "SM_USER", required = true) String smUser,
            @RequestParam("caseNumber") String caseNumber) {

        logger.info("GET /api/v1/case-data/set2/search - SM_USER: {}, caseNumber: {}", smUser, caseNumber);

        List<CaseDataResponse> data = caseDataService.getSet2DataByCaseNumber(caseNumber);
        return ResponseEntity.ok(data);
    }
}
