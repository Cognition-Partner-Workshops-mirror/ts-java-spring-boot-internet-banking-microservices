package com.casemanagement.repository;

import com.casemanagement.entity.CaseDataSet1;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository interface for CaseDataSet1 entity.
 * Provides standard CRUD operations and custom queries
 * for interacting with the CASE_DATA_SET1 table.
 */
@Repository
public interface CaseDataSet1Repository extends JpaRepository<CaseDataSet1, Long> {

    /** Find all records by case number */
    List<CaseDataSet1> findByCaseNumber(String caseNumber);

    /** Find all records by agent name */
    List<CaseDataSet1> findByAgentName(String agentName);

    /** Find all records by the SM_USER who submitted them */
    List<CaseDataSet1> findBySmUser(String smUser);
}
