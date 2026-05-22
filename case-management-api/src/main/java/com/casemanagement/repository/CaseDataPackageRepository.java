package com.casemanagement.repository;

import com.casemanagement.entity.CaseDataSet1;
import com.casemanagement.entity.CaseDataSet2;
import jakarta.persistence.EntityManager;
import jakarta.persistence.ParameterMode;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.StoredProcedureQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repository for calling Oracle stored procedures/packages.
 * In Oracle, data is passed through a PL/SQL package (CASE_DATA_PKG)
 * that handles the insert logic and any business rules.
 *
 * For H2 (dev profile), this uses direct JPA entity persistence
 * since H2 does not support Oracle PL/SQL packages.
 *
 * The active Spring profile determines the execution path:
 *   - Profile "oracle": calls CASE_DATA_PKG stored procedures
 *   - Profile "dev" (default): uses JPA entity persistence
 */
@Repository
public class CaseDataPackageRepository {

    private static final Logger logger = LoggerFactory.getLogger(CaseDataPackageRepository.class);

    @PersistenceContext
    private EntityManager entityManager;

    /** Datasource driver class used to detect Oracle vs H2 at runtime */
    @Value("${spring.datasource.driver-class-name:org.h2.Driver}")
    private String driverClassName;

    /**
     * Checks if the current datasource is Oracle.
     * Used to determine whether to call stored procedures or use JPA fallback.
     */
    private boolean isOracle() {
        return driverClassName != null && driverClassName.contains("oracle");
    }

    /**
     * Inserts data into CASE_DATA_SET1.
     * Routes to Oracle stored procedure or JPA persistence based on the active profile.
     *
     * @param caseNumber      the case number
     * @param versionNumber   the version number
     * @param agentName       the agent name
     * @param partyIdentifier the party identifier
     * @param smUser          the authenticated SM_USER
     * @return the generated record ID
     */
    @Transactional
    public Long insertCaseDataSet1(String caseNumber, String versionNumber,
                                   String agentName, String partyIdentifier, String smUser) {
        logger.info("Inserting Set1 data - caseNumber: {}, smUser: {}", caseNumber, smUser);

        if (isOracle()) {
            // Oracle environment: call the PL/SQL package procedure
            return callStoredProcedureSet1(caseNumber, versionNumber, agentName, partyIdentifier, smUser);
        } else {
            // Non-Oracle environment (H2): use JPA entity persistence
            return jpaInsertSet1(caseNumber, versionNumber, agentName, partyIdentifier, smUser);
        }
    }

    /**
     * Inserts data into CASE_DATA_SET2.
     * Routes to Oracle stored procedure or JPA persistence based on the active profile.
     *
     * @param caseNumber      the case number
     * @param versionNumber   the version number
     * @param agentName       the agent name
     * @param partyIdentifier the party identifier
     * @param smUser          the authenticated SM_USER
     * @return the generated record ID
     */
    @Transactional
    public Long insertCaseDataSet2(String caseNumber, String versionNumber,
                                   String agentName, String partyIdentifier, String smUser) {
        logger.info("Inserting Set2 data - caseNumber: {}, smUser: {}", caseNumber, smUser);

        if (isOracle()) {
            // Oracle environment: call the PL/SQL package procedure
            return callStoredProcedureSet2(caseNumber, versionNumber, agentName, partyIdentifier, smUser);
        } else {
            // Non-Oracle environment (H2): use JPA entity persistence
            return jpaInsertSet2(caseNumber, versionNumber, agentName, partyIdentifier, smUser);
        }
    }

    /**
     * Calls Oracle CASE_DATA_PKG.INSERT_CASE_DATA_SET1 stored procedure.
     * Only invoked when the datasource is Oracle.
     */
    private Long callStoredProcedureSet1(String caseNumber, String versionNumber,
                                         String agentName, String partyIdentifier, String smUser) {
        logger.info("Calling Oracle stored procedure CASE_DATA_PKG.INSERT_CASE_DATA_SET1");

        StoredProcedureQuery query = entityManager
                .createStoredProcedureQuery("CASE_DATA_PKG.INSERT_CASE_DATA_SET1");

        // Register input parameters matching the Oracle package specification
        query.registerStoredProcedureParameter("p_case_number", String.class, ParameterMode.IN);
        query.registerStoredProcedureParameter("p_version_number", String.class, ParameterMode.IN);
        query.registerStoredProcedureParameter("p_agent_name", String.class, ParameterMode.IN);
        query.registerStoredProcedureParameter("p_party_identifier", String.class, ParameterMode.IN);
        query.registerStoredProcedureParameter("p_sm_user", String.class, ParameterMode.IN);
        // Register output parameter for the generated record ID
        query.registerStoredProcedureParameter("p_record_id", Long.class, ParameterMode.OUT);

        // Set parameter values
        query.setParameter("p_case_number", caseNumber);
        query.setParameter("p_version_number", versionNumber);
        query.setParameter("p_agent_name", agentName);
        query.setParameter("p_party_identifier", partyIdentifier);
        query.setParameter("p_sm_user", smUser);

        query.execute();

        Long recordId = (Long) query.getOutputParameterValue("p_record_id");
        logger.info("Stored procedure Set1 completed. Record ID: {}", recordId);
        return recordId;
    }

    /**
     * Calls Oracle CASE_DATA_PKG.INSERT_CASE_DATA_SET2 stored procedure.
     * Only invoked when the datasource is Oracle.
     */
    private Long callStoredProcedureSet2(String caseNumber, String versionNumber,
                                         String agentName, String partyIdentifier, String smUser) {
        logger.info("Calling Oracle stored procedure CASE_DATA_PKG.INSERT_CASE_DATA_SET2");

        StoredProcedureQuery query = entityManager
                .createStoredProcedureQuery("CASE_DATA_PKG.INSERT_CASE_DATA_SET2");

        // Register input parameters matching the Oracle package specification
        query.registerStoredProcedureParameter("p_case_number", String.class, ParameterMode.IN);
        query.registerStoredProcedureParameter("p_version_number", String.class, ParameterMode.IN);
        query.registerStoredProcedureParameter("p_agent_name", String.class, ParameterMode.IN);
        query.registerStoredProcedureParameter("p_party_identifier", String.class, ParameterMode.IN);
        query.registerStoredProcedureParameter("p_sm_user", String.class, ParameterMode.IN);
        // Register output parameter for the generated record ID
        query.registerStoredProcedureParameter("p_record_id", Long.class, ParameterMode.OUT);

        // Set parameter values
        query.setParameter("p_case_number", caseNumber);
        query.setParameter("p_version_number", versionNumber);
        query.setParameter("p_agent_name", agentName);
        query.setParameter("p_party_identifier", partyIdentifier);
        query.setParameter("p_sm_user", smUser);

        query.execute();

        Long recordId = (Long) query.getOutputParameterValue("p_record_id");
        logger.info("Stored procedure Set2 completed. Record ID: {}", recordId);
        return recordId;
    }

    /**
     * JPA-based insert for Set1 - used in non-Oracle environments (H2 dev mode).
     * Creates and persists a CaseDataSet1 entity, letting Hibernate handle ID generation.
     */
    private Long jpaInsertSet1(String caseNumber, String versionNumber,
                               String agentName, String partyIdentifier, String smUser) {
        CaseDataSet1 entity = new CaseDataSet1(caseNumber, versionNumber, agentName,
                partyIdentifier, smUser);
        entityManager.persist(entity);
        entityManager.flush();
        logger.info("JPA insert Set1 completed. Record ID: {}", entity.getId());
        return entity.getId();
    }

    /**
     * JPA-based insert for Set2 - used in non-Oracle environments (H2 dev mode).
     * Creates and persists a CaseDataSet2 entity, letting Hibernate handle ID generation.
     */
    private Long jpaInsertSet2(String caseNumber, String versionNumber,
                               String agentName, String partyIdentifier, String smUser) {
        CaseDataSet2 entity = new CaseDataSet2(caseNumber, versionNumber, agentName,
                partyIdentifier, smUser);
        entityManager.persist(entity);
        entityManager.flush();
        logger.info("JPA insert Set2 completed. Record ID: {}", entity.getId());
        return entity.getId();
    }
}
