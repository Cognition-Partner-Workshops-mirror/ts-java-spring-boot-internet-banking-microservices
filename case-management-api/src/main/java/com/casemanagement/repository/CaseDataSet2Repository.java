package com.casemanagement.repository;

import com.casemanagement.entity.CaseDataSet2;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository interface for CaseDataSet2 entity.
 * Provides standard CRUD operations and custom queries
 * for interacting with the CASE_DATA_SET2 table.
 */
@Repository
public interface CaseDataSet2Repository extends JpaRepository<CaseDataSet2, Long> {

    /** Find all records by case number */
    List<CaseDataSet2> findByCaseNumber(String caseNumber);

    /** Find all records by agent name */
    List<CaseDataSet2> findByAgentName(String agentName);

    /** Find all records by the SM_USER who submitted them */
    List<CaseDataSet2> findBySmUser(String smUser);
}
