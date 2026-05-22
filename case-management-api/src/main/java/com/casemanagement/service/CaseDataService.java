package com.casemanagement.service;

import com.casemanagement.dto.CaseDataRequest;
import com.casemanagement.dto.CaseDataResponse;
import com.casemanagement.entity.CaseDataSet1;
import com.casemanagement.entity.CaseDataSet2;
import com.casemanagement.repository.CaseDataPackageRepository;
import com.casemanagement.repository.CaseDataSet1Repository;
import com.casemanagement.repository.CaseDataSet2Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service layer for case data operations.
 * Handles business logic for both Set 1 and Set 2 case data endpoints.
 * Delegates persistence to the Oracle package repository (stored procedure)
 * with fallback to JPA repository for non-Oracle environments.
 */
@Service
public class CaseDataService {

    private static final Logger logger = LoggerFactory.getLogger(CaseDataService.class);

    private final CaseDataSet1Repository set1Repository;
    private final CaseDataSet2Repository set2Repository;
    private final CaseDataPackageRepository packageRepository;

    @Autowired
    public CaseDataService(CaseDataSet1Repository set1Repository,
                           CaseDataSet2Repository set2Repository,
                           CaseDataPackageRepository packageRepository) {
        this.set1Repository = set1Repository;
        this.set2Repository = set2Repository;
        this.packageRepository = packageRepository;
    }

    /**
     * Process and store case data for Set 1.
     * Calls Oracle package procedure to insert data into CASE_DATA_SET1 table.
     *
     * @param request the case data request DTO
     * @param smUser  the authenticated user from SM_USER header
     * @return response DTO with persisted data details
     */
    @Transactional
    public CaseDataResponse processCaseDataSet1(CaseDataRequest request, String smUser) {
        logger.info("Processing Set1 data - caseNumber: {}, agent: {}, smUser: {}",
                request.getCaseNumber(), request.getAgentName(), smUser);

        // Call Oracle package/stored procedure to insert data
        Long recordId = packageRepository.insertCaseDataSet1(
                request.getCaseNumber(),
                request.getVersionNumber(),
                request.getAgentName(),
                request.getPartyIdentifier(),
                smUser
        );

        // Build and return response
        CaseDataResponse response = new CaseDataResponse();
        response.setId(recordId);
        response.setCaseNumber(request.getCaseNumber());
        response.setVersionNumber(request.getVersionNumber());
        response.setAgentName(request.getAgentName());
        response.setPartyIdentifier(request.getPartyIdentifier());
        response.setSmUser(smUser);
        response.setDataSet("SET1");
        response.setMessage("Case data successfully stored in Set 1");

        logger.info("Set1 data processed successfully. Record ID: {}", recordId);
        return response;
    }

    /**
     * Process and store case data for Set 2.
     * Calls Oracle package procedure to insert data into CASE_DATA_SET2 table.
     *
     * @param request the case data request DTO
     * @param smUser  the authenticated user from SM_USER header
     * @return response DTO with persisted data details
     */
    @Transactional
    public CaseDataResponse processCaseDataSet2(CaseDataRequest request, String smUser) {
        logger.info("Processing Set2 data - caseNumber: {}, agent: {}, smUser: {}",
                request.getCaseNumber(), request.getAgentName(), smUser);

        // Call Oracle package/stored procedure to insert data
        Long recordId = packageRepository.insertCaseDataSet2(
                request.getCaseNumber(),
                request.getVersionNumber(),
                request.getAgentName(),
                request.getPartyIdentifier(),
                smUser
        );

        // Build and return response
        CaseDataResponse response = new CaseDataResponse();
        response.setId(recordId);
        response.setCaseNumber(request.getCaseNumber());
        response.setVersionNumber(request.getVersionNumber());
        response.setAgentName(request.getAgentName());
        response.setPartyIdentifier(request.getPartyIdentifier());
        response.setSmUser(smUser);
        response.setDataSet("SET2");
        response.setMessage("Case data successfully stored in Set 2");

        logger.info("Set2 data processed successfully. Record ID: {}", recordId);
        return response;
    }

    /**
     * Retrieve all case data from Set 1.
     *
     * @return list of Set 1 case data responses
     */
    public List<CaseDataResponse> getAllSet1Data() {
        logger.info("Retrieving all Set1 data");
        return set1Repository.findAll().stream()
                .map(entity -> mapSet1ToResponse(entity))
                .collect(Collectors.toList());
    }

    /**
     * Retrieve all case data from Set 2.
     *
     * @return list of Set 2 case data responses
     */
    public List<CaseDataResponse> getAllSet2Data() {
        logger.info("Retrieving all Set2 data");
        return set2Repository.findAll().stream()
                .map(entity -> mapSet2ToResponse(entity))
                .collect(Collectors.toList());
    }

    /**
     * Retrieve Set 1 data by case number.
     *
     * @param caseNumber the case number to search for
     * @return list of matching Set 1 case data responses
     */
    public List<CaseDataResponse> getSet1DataByCaseNumber(String caseNumber) {
        logger.info("Retrieving Set1 data for caseNumber: {}", caseNumber);
        return set1Repository.findByCaseNumber(caseNumber).stream()
                .map(entity -> mapSet1ToResponse(entity))
                .collect(Collectors.toList());
    }

    /**
     * Retrieve Set 2 data by case number.
     *
     * @param caseNumber the case number to search for
     * @return list of matching Set 2 case data responses
     */
    public List<CaseDataResponse> getSet2DataByCaseNumber(String caseNumber) {
        logger.info("Retrieving Set2 data for caseNumber: {}", caseNumber);
        return set2Repository.findByCaseNumber(caseNumber).stream()
                .map(entity -> mapSet2ToResponse(entity))
                .collect(Collectors.toList());
    }

    /** Maps CaseDataSet1 entity to CaseDataResponse DTO */
    private CaseDataResponse mapSet1ToResponse(CaseDataSet1 entity) {
        CaseDataResponse response = new CaseDataResponse();
        response.setId(entity.getId());
        response.setCaseNumber(entity.getCaseNumber());
        response.setVersionNumber(entity.getVersionNumber());
        response.setAgentName(entity.getAgentName());
        response.setPartyIdentifier(entity.getPartyIdentifier());
        response.setSmUser(entity.getSmUser());
        response.setDataSet("SET1");
        response.setCreatedAt(entity.getCreatedAt());
        return response;
    }

    /** Maps CaseDataSet2 entity to CaseDataResponse DTO */
    private CaseDataResponse mapSet2ToResponse(CaseDataSet2 entity) {
        CaseDataResponse response = new CaseDataResponse();
        response.setId(entity.getId());
        response.setCaseNumber(entity.getCaseNumber());
        response.setVersionNumber(entity.getVersionNumber());
        response.setAgentName(entity.getAgentName());
        response.setPartyIdentifier(entity.getPartyIdentifier());
        response.setSmUser(entity.getSmUser());
        response.setDataSet("SET2");
        response.setCreatedAt(entity.getCreatedAt());
        return response;
    }
}
